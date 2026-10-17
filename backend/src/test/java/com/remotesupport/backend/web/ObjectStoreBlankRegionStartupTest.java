package com.remotesupport.backend.web;

import org.springframework.boot.test.context.SpringBootTest;

/** A blank OBJECT_STORE_REGION: the context starts, the ERROR names the setting, file storage answers 503. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"app.object-store.region="})
class ObjectStoreBlankRegionStartupTest extends ObjectStoreUnusableStartupCases {

  @Override
  String[] startupErrorMustName() {
    return new String[] {"OBJECT_STORE_REGION"};
  }
}
