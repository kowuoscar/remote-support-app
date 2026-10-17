package com.remotesupport.backend.web;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Whether file storage is available right now: one cached answer, kept fresh without any background
 * schedule (ADR 0007). {@link #probe()} runs once when the application is ready and never fails
 * startup. After that the store is only called when someone opens a Client Invoice page: {@link
 * #availableForPage()} returns the cached answer at once and, when the last completed check is over
 * 30 seconds old and none is in flight, starts one {@link #probe()} on a single-thread executor
 * (stale-while-revalidate). A failed transfer flips the answer at once through {@link
 * #markUnavailable()}; only a successful probe flips it back. Attaching and downloading read {@link
 * #isAvailable()} and never start a check.
 *
 * <p>The probe is {@code HeadBucket}, then {@code CreateBucket} when the bucket is absent and
 * creation is on, each call capped at about 3 seconds. Only the moves between up and down are
 * logged: one ERROR (endpoint, bucket, cause; never a credential) and one INFO on recovery.
 */
@Component
public class ObjectStoreAvailability {

  private static final Logger log = LoggerFactory.getLogger(ObjectStoreAvailability.class);

  static final Duration STALE_AFTER = Duration.ofSeconds(30);
  static final Duration CALL_TIMEOUT = Duration.ofSeconds(3);

  private final S3Client s3;
  private final ObjectStoreProperties props;
  private final Executor executor;
  private final ExecutorService ownedExecutor;
  private final Clock clock;

  private final AtomicBoolean probing = new AtomicBoolean();
  private volatile boolean available;
  private volatile Instant lastCheck;
  private volatile boolean reportedDown;

  @Autowired
  public ObjectStoreAvailability(S3Client s3, ObjectStoreProperties props) {
    this(s3, props, newProbeExecutor(), Clock.systemUTC(), true);
  }

  /** For tests: the executor and the clock are injected. */
  ObjectStoreAvailability(S3Client s3, ObjectStoreProperties props, Executor executor, Clock clock) {
    this(s3, props, executor, clock, false);
  }

  private ObjectStoreAvailability(
      S3Client s3, ObjectStoreProperties props, Executor executor, Clock clock, boolean owned) {
    this.s3 = s3;
    this.props = props;
    this.executor = executor;
    this.ownedExecutor = owned ? (ExecutorService) executor : null;
    this.clock = clock;
  }

  private static ExecutorService newProbeExecutor() {
    return Executors.newSingleThreadExecutor(
        r -> {
          Thread t = new Thread(r, "object-store-probe");
          t.setDaemon(true);
          return t;
        });
  }

  /** The cached answer. Never calls the store and never starts a check. */
  public boolean isAvailable() {
    return available;
  }

  /**
   * The cached answer at once; when the last completed check is over 30 seconds old and none is in
   * flight, also submits one {@link #probe()} and returns without waiting for it.
   */
  public boolean availableForPage() {
    boolean answer = available;
    Instant last = lastCheck;
    boolean stale = last == null || Duration.between(last, clock.instant()).compareTo(STALE_AFTER) > 0;
    if (stale && probing.compareAndSet(false, true)) {
      try {
        executor.execute(
            () -> {
              try {
                probe();
              } finally {
                probing.set(false);
              }
            });
      } catch (RuntimeException e) {
        probing.set(false);
      }
    }
    return answer;
  }

  /** A store failure was seen by a transfer. Does not touch the age of the last check. */
  public void markUnavailable() {
    available = false;
    reportedDown = true; // the failed transfer logged its own ERROR; a later failing probe stays silent
  }

  /** Ensures the bucket. Never throws. */
  public void probe() {
    try {
      if (ObjectStoreConfig.configurationProblem(props).isPresent()) {
        return; // already reported, once, when the client was built
      }
      try {
        ensureBucket();
        recovered();
      } catch (RuntimeException e) {
        failed(e);
      }
    } finally {
      lastCheck = clock.instant();
    }
  }

  private void ensureBucket() {
    try {
      s3.headBucket(
          HeadBucketRequest.builder()
              .bucket(props.bucket())
              .overrideConfiguration(c -> c.apiCallTimeout(CALL_TIMEOUT).apiCallAttemptTimeout(CALL_TIMEOUT))
              .build());
    } catch (S3Exception e) {
      if (e.statusCode() != 404 || !props.createBucket()) {
        throw e;
      }
      s3.createBucket(
          CreateBucketRequest.builder()
              .bucket(props.bucket())
              .overrideConfiguration(c -> c.apiCallTimeout(CALL_TIMEOUT).apiCallAttemptTimeout(CALL_TIMEOUT))
              .build());
      log.info("Created object store bucket {} at {}", props.bucket(), props.endpoint());
    }
  }

  private void recovered() {
    available = true;
    if (reportedDown) {
      reportedDown = false;
      log.info("Object store is available again at {} bucket {}", props.endpoint(), props.bucket());
    }
  }

  private void failed(RuntimeException e) {
    available = false;
    if (!reportedDown) {
      reportedDown = true;
      log.error(
          "Object store is unavailable: bucket check failed for endpoint {} bucket {} ({}). File"
              + " storage answers 503 until the store is reachable and the bucket exists.",
          props.endpoint(),
          props.bucket(),
          e instanceof S3Exception s3e ? "HTTP " + s3e.statusCode() : e.getClass().getSimpleName());
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  void onReady() {
    probe();
  }

  @PreDestroy
  void shutdown() {
    if (ownedExecutor != null) {
      ownedExecutor.shutdownNow();
    }
  }
}
