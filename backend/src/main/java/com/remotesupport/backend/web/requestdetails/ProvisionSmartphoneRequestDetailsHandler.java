package com.remotesupport.backend.web.requestdetails;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.web.InvalidRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * A {@link RequestType#PROVISION_SMARTPHONE} Request's own detail (request-types-and-flow spec's
 * table: "Provision Smartphone — Required: requested model"; provision-request-details ticket AC:
 * "A Provision Smartphone Request requires a requested model"). Self-contained like {@link
 * RebootRequestDetailsHandler}: this file plus {@code requestedModel} on {@link
 * RequestDetailsInput} is the whole slice. Provision no longer names a unit to replace (spec.md
 * Solution: "Provision no longer names a unit to replace — that is what Replace is for"), so,
 * unlike the older {@code replacesSmartphoneId} completion field, there is nothing here to
 * validate beyond the model — a Provision Smartphone Request never references an existing Fleet
 * unit at submission.
 */
@Component
public class ProvisionSmartphoneRequestDetailsHandler implements RequestDetailsHandler {

  @Override
  public RequestType type() {
    return RequestType.PROVISION_SMARTPHONE;
  }

  @Override
  public void apply(Contract contract, RequestDetailsInput input, Request request) {
    if (!StringUtils.hasText(input.requestedModel())) {
      throw new InvalidRequestException("A Provision Smartphone Request requires a requestedModel");
    }
    request.setRequestedModel(input.requestedModel().trim());
  }
}
