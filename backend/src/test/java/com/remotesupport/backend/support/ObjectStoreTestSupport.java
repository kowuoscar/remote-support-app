package com.remotesupport.backend.support;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * The shared test object store: one SeaweedFS container on the pinned tag, started once per JVM
 * beside {@link IntegrationTest}'s PostgreSQL and, like it, never stopped here (Testcontainers'
 * Ryuk reaps it). Spring contexts that boot (the {@code test} one and {@code
 * DemoDataLoaderApiTest}'s {@code demo} one) point at it through {@link #register}. The bucket
 * starts absent, so every context boot exercises the backend's create-bucket-at-startup path.
 *
 * <p>The helpers read the bucket through their own S3 client, so a test asserts what is really in
 * the store rather than what the application believes.
 */
public final class ObjectStoreTestSupport {

  /** Same pinned tag as docker-compose.yml and both e2e backend scripts. */
  public static final String SEAWEEDFS_IMAGE = "chrislusf/seaweedfs:4.48";

  public static final String ACCESS_KEY = "dev-only-access-key";
  public static final String SECRET_KEY = "dev-only-secret-key-change-me";
  public static final String BUCKET = "remote-support-files";

  @SuppressWarnings("resource")
  private static final GenericContainer<?> SEAWEEDFS =
      new GenericContainer<>(DockerImageName.parse(SEAWEEDFS_IMAGE))
          .withEnv("AWS_ACCESS_KEY_ID", ACCESS_KEY)
          .withEnv("AWS_SECRET_ACCESS_KEY", SECRET_KEY)
          .withCommand("server", "-dir=/data", "-s3")
          .withExposedPorts(8333)
          .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCode(403));

  static {
    SEAWEEDFS.start();
  }

  private static final S3Client CLIENT =
      S3Client.builder()
          .endpointOverride(URI.create(endpoint()))
          .region(Region.US_EAST_1)
          .credentialsProvider(
              StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
          .forcePathStyle(true)
          .build();

  private ObjectStoreTestSupport() {}

  public static String endpoint() {
    return "http://" + SEAWEEDFS.getHost() + ":" + SEAWEEDFS.getMappedPort(8333);
  }

  /**
   * Points a booting Spring context at the shared store. It sets the {@code OBJECT_STORE_*}
   * placeholders that {@code application.yml} reads, not the {@code app.object-store.*} keys
   * themselves: a dynamic property outranks a test's inline {@code properties}, so registering the
   * keys would stop a context test from booting with one setting wrong.
   */
  public static void register(DynamicPropertyRegistry registry) {
    registry.add("OBJECT_STORE_ENDPOINT", ObjectStoreTestSupport::endpoint);
    registry.add("OBJECT_STORE_ACCESS_KEY", () -> ACCESS_KEY);
    registry.add("OBJECT_STORE_SECRET_KEY", () -> SECRET_KEY);
    registry.add("OBJECT_STORE_BUCKET", () -> BUCKET);
  }

  /** The keys under {@code prefix} in {@link #BUCKET}; empty when the bucket does not exist. */
  public static List<String> keysUnder(String prefix) {
    return keysUnder(BUCKET, prefix);
  }

  public static List<String> keysUnder(String bucket, String prefix) {
    try {
      return CLIENT
          .listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())
          .contents()
          .stream()
          .map(S3Object::key)
          .toList();
    } catch (NoSuchBucketException e) {
      return List.of();
    }
  }

  public static byte[] read(String key) {
    return CLIENT
        .getObject(GetObjectRequest.builder().bucket(BUCKET).key(key).build(), ResponseTransformer.toBytes())
        .asByteArray();
  }

  /** The object's {@code x-amz-meta-*} user metadata, as the store returns it on a HEAD. */
  public static Map<String, String> userMetadata(String key) {
    return CLIENT.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build()).metadata();
  }

  public static int tagCount(String key) {
    return CLIENT.getObjectTagging(GetObjectTaggingRequest.builder().bucket(BUCKET).key(key).build()).tagSet().size();
  }

  public static void delete(String key) {
    CLIENT.deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key(key).build());
  }

  public static boolean bucketExists(String bucket) {
    try {
      CLIENT.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
      return true;
    } catch (NoSuchBucketException e) {
      return false;
    } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
      if (e.statusCode() == 404) {
        return false;
      }
      throw e;
    }
  }
}
