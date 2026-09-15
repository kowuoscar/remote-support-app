package com.remotesupport.backend.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for HTTP-API-seam integration tests: boots the full Spring context against a real
 * PostgreSQL 16 instance provided by Testcontainers, and exposes {@link MockMvc} for driving
 * requests through the real REST controllers.
 *
 * <p>The container is started once, in a static initializer, and deliberately never stopped by
 * this class ("singleton container" pattern) so it is shared across every subclass in the run:
 * Testcontainers' own {@code @Container}/{@code @Testcontainers} lifecycle stops the container in
 * each test class's {@code afterAll}, which breaks every subsequent test class sharing the same
 * static field. The JVM shutdown hook Testcontainers registers (via Ryuk) tears it down when the
 * test run ends.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  static {
    POSTGRES.start();
  }

  @Autowired protected MockMvc mockMvc;
}
