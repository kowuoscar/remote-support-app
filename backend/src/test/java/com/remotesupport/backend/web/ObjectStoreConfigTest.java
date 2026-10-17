package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Every setting {@link ObjectStoreConfig#configurationProblem} checks, one row each. */
class ObjectStoreConfigTest {

  private static ObjectStoreProperties props(String endpoint, String region, String bucket, String accessKey, String secretKey) {
    return new ObjectStoreProperties(endpoint, region, bucket, accessKey, secretKey, true, true);
  }

  static Stream<Arguments> unusable() {
    String url = "http://store:8333";
    return Stream.of(
        Arguments.of(props(null, "r", "b", "a", "s"), "OBJECT_STORE_ENDPOINT"),
        Arguments.of(props("not a url", "r", "b", "a", "s"), "OBJECT_STORE_ENDPOINT"),
        Arguments.of(props("ftp://store", "r", "b", "a", "s"), "OBJECT_STORE_ENDPOINT"),
        Arguments.of(props(url, "r", null, "a", "s"), "OBJECT_STORE_BUCKET"),
        Arguments.of(props(url, "r", " ", "a", "s"), "OBJECT_STORE_BUCKET"),
        Arguments.of(props(url, "", "b", "a", "s"), "OBJECT_STORE_REGION"),
        Arguments.of(props(url, "r", "b", "", "s"), "OBJECT_STORE_ACCESS_KEY"),
        Arguments.of(props(url, "r", "b", "a", " "), "OBJECT_STORE_SECRET_KEY"));
  }

  @ParameterizedTest
  @MethodSource("unusable")
  void anUnusableSettingIsNamed(ObjectStoreProperties props, String setting) {
    assertThat(ObjectStoreConfig.configurationProblem(props)).hasValueSatisfying(p -> assertThat(p).contains(setting));
  }

  @ParameterizedTest
  @MethodSource("usable")
  void aUsableConfigurationHasNoProblem(ObjectStoreProperties props) {
    assertThat(ObjectStoreConfig.configurationProblem(props)).isEmpty();
  }

  static Stream<ObjectStoreProperties> usable() {
    return Stream.of(props("http://store:8333", "r", "b", "a", "s"), props(" https://s3.example.com ", "r", "b", "a", "s"));
  }
}
