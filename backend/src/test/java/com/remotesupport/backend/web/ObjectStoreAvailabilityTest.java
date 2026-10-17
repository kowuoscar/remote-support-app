package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * {@link ObjectStoreAvailability} over a mocked {@link S3Client}, an executor that records tasks and
 * runs them only when told, and a clock moved by hand.
 */
@ExtendWith(OutputCaptureExtension.class)
class ObjectStoreAvailabilityTest {

  private static final String ACCESS_KEY = "AKIA-secret-access-key";
  private static final String SECRET_KEY = "very-secret-key-value";

  private S3Client s3;
  private final List<Runnable> submitted = new ArrayList<>();
  private final Executor manualExecutor = submitted::add;
  private MutableClock clock;

  @BeforeEach
  void setUp() {
    s3 = mock(S3Client.class);
    clock = new MutableClock(Instant.parse("2026-10-17T10:00:00Z"));
  }

  private ObjectStoreAvailability availability(boolean createBucket) {
    return new ObjectStoreAvailability(
        s3,
        new ObjectStoreProperties("http://store:8333", "us-east-1", "the-bucket", ACCESS_KEY, SECRET_KEY, true, createBucket),
        manualExecutor,
        clock);
  }

  private void runSubmitted() {
    List<Runnable> tasks = new ArrayList<>(submitted);
    submitted.clear();
    tasks.forEach(Runnable::run);
  }

  private static S3Exception s3Error(int status) {
    return (S3Exception)
        S3Exception.builder().statusCode(status).awsErrorDetails(AwsErrorDetails.builder().errorMessage("x").build()).build();
  }

  @Test
  void isUnavailableUntilTheFirstProbeSucceeds() {
    ObjectStoreAvailability availability = availability(true);

    assertThat(availability.isAvailable()).isFalse();

    availability.probe();

    assertThat(availability.isAvailable()).isTrue();
  }

  @Test
  void anUnreachableStoreIsUnavailableAndProbeNeverThrows() {
    when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(SdkClientException.create("connect refused"));
    ObjectStoreAvailability availability = availability(true);

    assertThatCode(availability::probe).doesNotThrowAnyException();

    assertThat(availability.isAvailable()).isFalse();
  }

  @Test
  void refusedCredentialsAreUnavailableAndTheErrorNamesNeitherKey(CapturedOutput output) {
    when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(s3Error(403));
    ObjectStoreAvailability availability = availability(true);

    assertThatCode(availability::probe).doesNotThrowAnyException();

    assertThat(availability.isAvailable()).isFalse();
    assertThat(output.getAll()).contains("the-bucket").contains("http://store:8333").contains("HTTP 403");
    assertThat(output.getAll()).doesNotContain(ACCESS_KEY).doesNotContain(SECRET_KEY);
  }

  @Test
  void anAbsentBucketWithCreationOffIsUnavailableAndNotCreated() {
    when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(s3Error(404));
    ObjectStoreAvailability availability = availability(false);

    assertThatCode(availability::probe).doesNotThrowAnyException();

    assertThat(availability.isAvailable()).isFalse();
    verify(s3, never()).createBucket(any(CreateBucketRequest.class));
  }

  @Test
  void anAbsentBucketWithCreationOnIsCreatedAndAvailable() {
    when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(s3Error(404));
    ObjectStoreAvailability availability = availability(true);

    availability.probe();

    assertThat(availability.isAvailable()).isTrue();
    verify(s3).createBucket(any(CreateBucketRequest.class));
  }

  @Test
  void aPresentBucketIsNotRecreated() {
    when(s3.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
    ObjectStoreAvailability availability = availability(true);

    availability.probe();

    assertThat(availability.isAvailable()).isTrue();
    verify(s3, never()).createBucket(any(CreateBucketRequest.class));
  }

  @Test
  void aStaleValueIsServedAtOnceAndExactlyOneProbeIsSubmittedWhoseResultTheNextCallReturns() {
    ObjectStoreAvailability availability = availability(true);
    availability.probe(); // available, checked at t0
    clock.advance(Duration.ofSeconds(31));
    when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(SdkClientException.create("down"));

    assertThat(availability.availableForPage()).isTrue(); // the cached value, before any probe ran
    assertThat(submitted).hasSize(1);
    verify(s3, times(1)).headBucket(any(HeadBucketRequest.class)); // only the first probe

    runSubmitted();

    assertThat(availability.availableForPage()).isFalse();
  }

  @Test
  void beforeAnyProbeTheFirstPageStartsOne() {
    ObjectStoreAvailability availability = availability(true);

    assertThat(availability.availableForPage()).isFalse();

    assertThat(submitted).hasSize(1);
  }

  @Test
  void severalPagesWhileAProbeIsSubmittedSubmitNoSecondOneAndAnotherFollowsOnceItFinishes() {
    ObjectStoreAvailability availability = availability(true);

    availability.availableForPage();
    availability.availableForPage();
    availability.availableForPage();

    assertThat(submitted).hasSize(1);

    runSubmitted();
    clock.advance(Duration.ofSeconds(31));
    availability.availableForPage();

    assertThat(submitted).hasSize(1);
  }

  @Test
  void noRefreshWithinThirtySecondsAndIsAvailableNeverSubmits() {
    ObjectStoreAvailability availability = availability(true);
    availability.probe();

    clock.advance(Duration.ofSeconds(30));
    availability.availableForPage();
    assertThat(submitted).isEmpty();

    clock.advance(Duration.ofSeconds(1));
    availability.isAvailable();
    assertThat(submitted).isEmpty();
    clock.advance(Duration.ofHours(5));
    availability.isAvailable();
    assertThat(submitted).isEmpty();
  }

  @Test
  void markUnavailableFlipsAtOnceAndOnlyASuccessfulProbeRecovers() {
    ObjectStoreAvailability availability = availability(true);
    availability.probe();
    assertThat(availability.isAvailable()).isTrue();

    availability.markUnavailable();
    assertThat(availability.isAvailable()).isFalse();

    clock.advance(Duration.ofSeconds(5));
    availability.availableForPage();
    assertThat(submitted).isEmpty();
    assertThat(availability.isAvailable()).isFalse();

    availability.probe();
    assertThat(availability.isAvailable()).isTrue();
  }

  @Test
  void markUnavailableDoesNotResetTheAgeOfTheLastCheck() {
    ObjectStoreAvailability availability = availability(true);
    availability.probe();
    clock.advance(Duration.ofSeconds(31));

    availability.markUnavailable();
    availability.availableForPage();

    assertThat(submitted).hasSize(1);
  }

  @Test
  void oneErrorOnTheMoveToUnavailableNoneOnASecondFailureOneInfoOnRecovery(CapturedOutput output) {
    ObjectStoreAvailability availability = availability(true);
    availability.probe();
    when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(SdkClientException.create("down"));

    availability.probe();
    availability.probe();

    assertThat(count(output, " ERROR ")).isEqualTo(1);

    when(s3.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
    availability.probe();
    availability.probe();

    assertThat(count(output, " ERROR ")).isEqualTo(1);
    assertThat(count(output, "Object store is available again")).isEqualTo(1);
  }

  @Test
  void aStoreThatIsDownAtStartupLogsOneErrorWhateverHowManyPagesOpen(CapturedOutput output) {
    when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(SdkClientException.create("down"));
    ObjectStoreAvailability availability = availability(true);

    availability.probe();
    for (int i = 0; i < 3; i++) {
      clock.advance(Duration.ofSeconds(31));
      availability.availableForPage();
      runSubmitted();
    }

    assertThat(count(output, " ERROR ")).isEqualTo(1);
  }

  private static long count(CapturedOutput output, String needle) {
    return output.getAll().lines().filter(l -> l.contains(needle)).count();
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
