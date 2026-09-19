package com.remotesupport.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/**
 * A monthly plan a Carrier sells for a Postpaid SIM. Its price is copied onto a SIM Card as the
 * monthly fee when the SIM Card is created; a later price change never touches SIM Cards already
 * on the Plan.
 */
@Entity
@Table(name = "postpaid_plans")
@NoArgsConstructor
public class PostpaidPlan extends CarrierOffer {}
