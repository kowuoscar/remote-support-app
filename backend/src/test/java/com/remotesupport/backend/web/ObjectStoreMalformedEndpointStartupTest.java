package com.remotesupport.backend.web;

import org.springframework.boot.test.context.SpringBootTest;

/** A malformed OBJECT_STORE_ENDPOINT: the context starts, the ERROR names the setting, file storage answers 503. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"app.object-store.endpoint=not a url"})
class ObjectStoreMalformedEndpointStartupTest extends ObjectStoreUnusableStartupCases {

  @Override
  String[] startupErrorMustName() {
    return new String[] {"OBJECT_STORE_ENDPOINT"};
  }
}
