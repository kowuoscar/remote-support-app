package com.remotesupport.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/**
 * A topup a Carrier sells. Its price is only the suggested amount of a Topup Fee — the Fee keeps
 * whatever amount the Agent submits, so a later price change never touches an existing Fee.
 */
@Entity
@Table(name = "topup_options")
@NoArgsConstructor
public class TopupOption extends CarrierOffer {}
