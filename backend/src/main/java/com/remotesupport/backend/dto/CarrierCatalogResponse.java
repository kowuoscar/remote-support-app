package com.remotesupport.backend.dto;

import java.util.List;

/**
 * One Country's Carriers, with the currency every price in that catalog is in — so a reader (an
 * Agent's page, which never chose the Country) knows whose catalog it is looking at. Active
 * Carriers come first, then archived ones; each group is ordered by name.
 */
public record CarrierCatalogResponse(String country, String currency, List<CarrierResponse> carriers) {}
