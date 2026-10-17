package com.remotesupport.backend.web;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Builds the one {@link S3Client}, configured for a non-AWS store (ADR 0007): endpoint override,
 * static credentials, addressing style from the settings, checksums only when required, and short
 * timeouts so an unreachable store fails an attach in seconds.
 *
 * <p>Nothing about the store may fail the Spring context. A setting that cannot build a client
 * logs one ERROR naming the setting (never a credential value) and yields a client whose every
 * call fails with an SDK exception, which the storage turns into a 503.
 */
@Configuration
@EnableConfigurationProperties(ObjectStoreProperties.class)
class ObjectStoreConfig {

  private static final Logger log = LoggerFactory.getLogger(ObjectStoreConfig.class);

  @Bean(destroyMethod = "close")
  S3Client s3Client(ObjectStoreProperties props) {
    Optional<String> problem = configurationProblem(props);
    if (problem.isPresent()) {
      log.error(
          "Object store configuration is unusable: {}. File storage is unavailable until the"
              + " configuration is fixed and the backend restarted.",
          problem.get());
      return unusableClient();
    }
    try {
      return S3Client.builder()
          .endpointOverride(URI.create(props.endpoint().trim()))
          .region(Region.of(props.region().trim()))
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
          .forcePathStyle(props.pathStyleAccess())
          .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
          .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
          .overrideConfiguration(
              c -> c.apiCallAttemptTimeout(Duration.ofSeconds(10)).apiCallTimeout(Duration.ofSeconds(30)))
          .build();
    } catch (RuntimeException e) {
      log.error(
          "Object store client could not be built ({}). File storage is unavailable until the"
              + " configuration is fixed and the backend restarted.",
          e.getClass().getSimpleName());
      return unusableClient();
    }
  }

  /** The name of the first environment variable whose value cannot build a client, if any. */
  static Optional<String> configurationProblem(ObjectStoreProperties props) {
    if (props.endpoint() == null || !isHttpUrl(props.endpoint().trim())) {
      return Optional.of("OBJECT_STORE_ENDPOINT is not a http(s) URL");
    }
    if (isBlank(props.bucket())) {
      return Optional.of("OBJECT_STORE_BUCKET is blank");
    }
    if (isBlank(props.region())) {
      return Optional.of("OBJECT_STORE_REGION is blank");
    }
    if (isBlank(props.accessKey())) {
      return Optional.of("OBJECT_STORE_ACCESS_KEY is blank");
    }
    if (isBlank(props.secretKey())) {
      return Optional.of("OBJECT_STORE_SECRET_KEY is blank");
    }
    return Optional.empty();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static boolean isHttpUrl(String value) {
    try {
      URI uri = URI.create(value);
      return uri.getHost() != null
          && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static S3Client unusableClient() {
    return (S3Client)
        Proxy.newProxyInstance(
            S3Client.class.getClassLoader(),
            new Class<?>[] {S3Client.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "close":
                  return null;
                case "serviceName":
                  return "s3";
                case "toString":
                  return "UnusableS3Client";
                case "hashCode":
                  return System.identityHashCode(proxy);
                case "equals":
                  return proxy == args[0];
                default:
                  break;
              }
              throw SdkClientException.create("Object store is not configured");
            });
  }
}
