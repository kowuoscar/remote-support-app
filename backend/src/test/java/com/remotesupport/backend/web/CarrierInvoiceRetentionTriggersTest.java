package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.ObjectStoreTestSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.Task;
import org.springframework.scheduling.config.TriggerTask;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Which triggers the retention settings register, read from Spring's {@link ScheduledTaskHolder}.
 * Each case boots its own context with the settings under test, so none extends {@link
 * IntegrationTest}, whose dynamic properties switch retention off. The startup run is shown in the
 * walkthrough, not waited on.
 */
class CarrierInvoiceRetentionTriggersTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", IntegrationTest.POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", IntegrationTest.POSTGRES::getUsername);
    registry.add("spring.datasource.password", IntegrationTest.POSTGRES::getPassword);
    ObjectStoreTestSupport.register(registry);
  }

  private static List<CronTrigger> cronTriggers(ObjectProvider<ScheduledTaskHolder> holders) {
    return holders.stream()
        .flatMap(holder -> holder.getScheduledTasks().stream())
        .map(ScheduledTask::getTask)
        .map(CarrierInvoiceRetentionTriggersTest::cronTriggerOf)
        .toList();
  }

  /** The trigger's next run after 10:00:30 UTC on a fixed day, which shows the zone it reads its cron in. */
  private static Instant nextRun(CronTrigger trigger) {
    Clock clock = Clock.fixed(Instant.parse("2026-10-18T10:00:30Z"), ZoneOffset.UTC);
    return trigger.nextExecution(new SimpleTriggerContext(clock));
  }

  private static CronTrigger cronTriggerOf(Task task) {
    if (task instanceof CronTask cron) {
      return (CronTrigger) cron.getTrigger();
    }
    return (CronTrigger) ((TriggerTask) task).getTrigger();
  }

  @Nested
  @SpringBootTest(properties = "app.carrier-invoice-retention.enabled=false")
  @ActiveProfiles("test")
  class Disabled {
    @Autowired ObjectProvider<ScheduledTaskHolder> holders;
    @Autowired ObjectProvider<CarrierInvoiceRetention> job;

    @Test
    void registersNoScheduledTaskYetTheJobCanStillRun() {
      assertThat(cronTriggers(holders)).isEmpty();
      assertThat(job.getIfAvailable()).isNotNull();
    }
  }

  @Nested
  @SpringBootTest
  @ActiveProfiles("test")
  class EnabledByDefault {
    @Autowired ObjectProvider<ScheduledTaskHolder> holders;

    @Test
    void registersTheDailyCronAtThreeInUtc() {
      List<CronTrigger> triggers = cronTriggers(holders);

      assertThat(triggers).hasSize(1);
      assertThat(triggers.get(0).getExpression()).isEqualTo("0 0 3 * * *");
      assertThat(nextRun(triggers.get(0))).isEqualTo(Instant.parse("2026-10-19T03:00:00Z"));
    }
  }

  @Nested
  @SpringBootTest(properties = {"app.carrier-invoice-retention.enabled=true", "app.carrier-invoice-retention.cron=0 * * * * *"})
  @ActiveProfiles("test")
  class CronOverridden {
    @Autowired ObjectProvider<ScheduledTaskHolder> holders;

    @Test
    void registersTheConfiguredCronInstead() {
      List<CronTrigger> triggers = cronTriggers(holders);

      assertThat(triggers).hasSize(1);
      assertThat(triggers.get(0).getExpression()).isEqualTo("0 * * * * *");
      assertThat(nextRun(triggers.get(0))).isEqualTo(Instant.parse("2026-10-18T10:01:00Z"));
    }
  }
}
