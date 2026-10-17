package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.ObjectStoreTestSupport;
import org.junit.jupiter.api.Test;

/**
 * The shared test bucket starts absent in a fresh store, so a booted context having created it shows
 * the create-bucket-at-startup path (OBJECT_STORE_CREATE_BUCKET on, the default).
 */
class ObjectStoreBucketCreatedAtStartupTest extends IntegrationTest {

  @Test
  void aMissingBucketIsCreatedAtBoot() {
    assertThat(ObjectStoreTestSupport.bucketExists(ObjectStoreTestSupport.BUCKET)).isTrue();
  }
}
