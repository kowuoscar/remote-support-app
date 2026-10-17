package com.remotesupport.backend.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Checks once, when the application is ready, that the bucket exists ({@code HeadBucket}), and
 * creates it when absent and {@code OBJECT_STORE_CREATE_BUCKET} is on. It never fails startup: a
 * store that is unreachable, refuses the credentials, or lacks the bucket with creation off is one
 * ERROR naming the endpoint and the bucket, and the backend starts anyway (ADR 0007). Attaching and
 * downloading answer 503 until a check has passed ({@link #passed()}), so a store that only
 * auto-creates buckets on write cannot hide a wrong bucket name.
 */
@Component
public class ObjectStoreBucketCheck {

  private static final Logger log = LoggerFactory.getLogger(ObjectStoreBucketCheck.class);

  private final S3Client s3;
  private final ObjectStoreProperties props;
  private volatile boolean passed;

  public ObjectStoreBucketCheck(S3Client s3, ObjectStoreProperties props) {
    this.s3 = s3;
    this.props = props;
  }

  /** Whether the startup check found, or created, the bucket. */
  public boolean passed() {
    return passed;
  }

  @EventListener(ApplicationReadyEvent.class)
  void check() {
    if (ObjectStoreConfig.configurationProblem(props).isPresent()) {
      return; // already reported, once, when the client was built
    }
    try {
      try {
        s3.headBucket(HeadBucketRequest.builder().bucket(props.bucket()).build());
      } catch (S3Exception e) {
        if (e.statusCode() != 404 || !props.createBucket()) {
          throw e;
        }
        s3.createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
        log.info("Created object store bucket {} at {}", props.bucket(), props.endpoint());
      }
      passed = true;
    } catch (SdkException e) {
      log.error(
          "Object store bucket check failed for endpoint {} bucket {} ({}). File storage will"
              + " answer 503 until the store is reachable and the bucket exists.",
          props.endpoint(),
          props.bucket(),
          e instanceof S3Exception s3e ? "HTTP " + s3e.statusCode() : e.getClass().getSimpleName());
    }
  }
}
