package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.remotesupport.backend.support.ObjectStoreTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * A bucket that does not exist with OBJECT_STORE_CREATE_BUCKET=false (how Hetzner is run): the
 * context starts, the ERROR names the endpoint and the bucket, file storage answers 503, and no
 * bucket is created.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"app.object-store.bucket=bucket-that-is-never-created", "app.object-store.create-bucket=false"})
class ObjectStoreMissingBucketStartupTest extends ObjectStoreUnusableStartupCases {

  @Override
  String[] startupErrorMustName() {
    return new String[] {"bucket-that-is-never-created", "http://"};
  }

  @Test
  void noBucketIsCreated() {
    assertThat(ObjectStoreTestSupport.bucketExists("bucket-that-is-never-created")).isFalse();
  }
}
