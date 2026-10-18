package com.remotesupport.backend.web;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/**
 * The app's first scheduled task: {@link CarrierInvoiceRetention} on a cron in UTC, and once about
 * a minute after startup so a stack that is off at the cron's hour still catches up. Both run on
 * Spring's scheduling thread, never on the startup path. Switched off as a whole by {@code
 * app.carrier-invoice-retention.enabled=false}, which also leaves scheduling off.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CarrierInvoiceRetentionProperties.class)
@ConditionalOnProperty(
    prefix = "app.carrier-invoice-retention",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
class CarrierInvoiceRetentionScheduling implements SchedulingConfigurer {

  static final Duration STARTUP_DELAY = Duration.ofMinutes(1);

  private final CarrierInvoiceRetentionProperties props;
  private final CarrierInvoiceRetention retention;
  private final TaskScheduler scheduler;

  CarrierInvoiceRetentionScheduling(
      CarrierInvoiceRetentionProperties props, CarrierInvoiceRetention retention, TaskScheduler scheduler) {
    this.props = props;
    this.retention = retention;
    this.scheduler = scheduler;
  }

  @Override
  public void configureTasks(ScheduledTaskRegistrar registrar) {
    registrar.addCronTask(new CronTask(retention::runOnce, new CronTrigger(props.cron(), ZoneOffset.UTC)));
  }

  @EventListener(ApplicationReadyEvent.class)
  void scheduleStartupRun() {
    scheduler.schedule(retention::runOnce, Instant.now().plus(STARTUP_DELAY));
  }
}
