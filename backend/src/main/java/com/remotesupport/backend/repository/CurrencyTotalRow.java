package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Currency;
import java.math.BigDecimal;

/** One currency's summed invoice total, as the grouped Tenant-totals queries return it. */
public record CurrencyTotalRow(Currency currency, BigDecimal amount) {}
