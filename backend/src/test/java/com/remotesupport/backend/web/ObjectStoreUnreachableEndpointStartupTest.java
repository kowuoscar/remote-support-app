package com.remotesupport.backend.web;

import org.springframework.boot.test.context.SpringBootTest;

/** An endpoint nothing listens on: the context starts, the ERROR names the endpoint and the bucket, file storage answers 503. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"app.object-store.endpoint=http://localhost:1"})
class ObjectStoreUnreachableEndpointStartupTest extends ObjectStoreUnusableStartupCases {

  @Override
  String[] startupErrorMustName() {
    return new String[] {"http://localhost:1", "remote-support-files"};
  }
}
