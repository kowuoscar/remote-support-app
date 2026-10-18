package com.remotesupport.backend.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Carrier Invoice File retention job's triggers, bound from {@code
 * app.carrier-invoice-retention}. The 30 days and the batch of 500 are constants of {@link
 * CarrierInvoiceRetention}, not settings.
 *
 * @param enabled {@code CARRIER_INVOICE_RETENTION_ENABLED}: {@code false} registers neither the cron
 *     nor the startup run; {@link CarrierInvoiceRetention#runOnce()} still works
 * @param cron {@code CARRIER_INVOICE_RETENTION_CRON}, a Spring six-field cron, always in zone UTC
 */
@ConfigurationProperties("app.carrier-invoice-retention")
public record CarrierInvoiceRetentionProperties(boolean enabled, String cron) {}
