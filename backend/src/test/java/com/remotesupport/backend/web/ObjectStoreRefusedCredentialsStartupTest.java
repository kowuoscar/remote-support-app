package com.remotesupport.backend.web;

import org.springframework.boot.test.context.SpringBootTest;

/** Credentials the store refuses: the context starts, the one ERROR names the bucket and holds neither credential. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "app.object-store.access-key=" + ObjectStoreUnusableStartupCases.WRONG_ACCESS_KEY,
      "app.object-store.secret-key=" + ObjectStoreUnusableStartupCases.WRONG_SECRET_KEY
    })
class ObjectStoreRefusedCredentialsStartupTest extends ObjectStoreUnusableStartupCases {

  @Override
  String[] startupErrorMustName() {
    return new String[] {"remote-support-files"};
  }
}
