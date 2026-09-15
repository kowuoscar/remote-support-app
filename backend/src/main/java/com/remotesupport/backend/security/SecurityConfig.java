package com.remotesupport.backend.security;

import com.remotesupport.backend.logging.RequestLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless, JWT-based security: no sessions, no CSRF (no cookies are used for auth), public
 * health and login endpoints, everything else requires a valid bearer token. The JWT filter
 * populates the security context from the token alone (no DB round-trip per request); the
 * request-logging filter runs immediately after it so structured logs can carry tenant/user id.
 */
@Configuration
public class SecurityConfig {

  private final JwtService jwtService;

  public SecurityConfig(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService);

    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/health", "/api/auth/login", "/actuator/health")
                    .permitAll()
                    // Spring Boot's ErrorPageFilter forwards a sendError() (e.g. AccessDeniedHandler's
                    // 403) to /error as an internal dispatch. On a real servlet container (unlike
                    // MockMvc, which never exercises this) that forward re-runs this filter chain,
                    // but JwtAuthenticationFilter doesn't re-authenticate it, so an unguarded
                    // `.anyRequest().authenticated()` would deny the anonymous /error request and
                    // silently clobber the original 403 into a misleading 401. /error must be
                    // permitAll so the real status (set before the forward) is the one that ships.
                    .requestMatchers("/error")
                    .permitAll()
                    // fleet-management ticket: an Agent/Tester needs their own scoped Contract
                    // list (to switch between Contracts when viewing Fleet) and Fleet access
                    // itself, but neither Manager-only ownership rule below fits a per-resource
                    // "is this my Contract" check — that's enforced in ContractController/the
                    // Fleet controllers themselves. These matchers must precede the broader
                    // Manager-only ones so they win (first match wins).
                    .requestMatchers(HttpMethod.GET, "/api/contracts")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/contracts/*/smartphones", "/api/contracts/*/sim-cards")
                    .hasRole("MANAGER")
                    .requestMatchers("/api/contracts/*/smartphones/**", "/api/contracts/*/sim-cards/**")
                    .authenticated()
                    // tester-request-submission / agent-request-fulfillment tickets: a Tester
                    // submits their own Request, or the Contract's own Agent logs one
                    // proactively on a Tester's behalf (spec.md Access control); which of the
                    // two, and Contract ownership, is enforced in
                    // RequestController/RequestAccessGuard, same as the Fleet matchers above.
                    // Viewing and status changes stay open to any authenticated role at the
                    // matcher level for the same reason.
                    .requestMatchers(HttpMethod.POST, "/api/contracts/*/requests")
                    .hasAnyRole("TESTER", "AGENT")
                    .requestMatchers("/api/contracts/*/requests/**")
                    .authenticated()
                    // agent-request-fulfillment ticket: the Agent needs to pick which Tester a
                    // proactively-logged Request is raised on behalf of; scoped per-Contract via
                    // FleetAccessGuard in ContractTestersController, same shape as Fleet/Requests.
                    .requestMatchers(HttpMethod.GET, "/api/contracts/*/testers")
                    .authenticated()
                    // fee-logging-and-provisioning ticket: only the Contract's own Agent (or a
                    // Manager) may log a Fee (spec.md Access control: "Agent: full CRUD on ...
                    // Fees ... within their own Contracts"); Contract ownership itself is
                    // enforced in FeeController/RequestAccessGuard, same shape as Requests above.
                    // Viewing stays open to any authenticated role at the matcher level, scoped
                    // per-Contract like everything else nested under one.
                    .requestMatchers(HttpMethod.POST, "/api/contracts/*/fees")
                    .hasAnyRole("MANAGER", "AGENT")
                    .requestMatchers("/api/contracts/*/fees/**")
                    .authenticated()
                    // Manager-only entity setup (manager-entity-setup ticket): Client, Tester
                    // (nested under /api/clients/{id}/testers), Agent and Contract creation and
                    // listing are all Manager-only; an Agent or Tester request is rejected 403.
                    .requestMatchers("/api/clients/**", "/api/agents/**", "/api/contracts/**")
                    .hasRole("MANAGER")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(new RequestLoggingFilter(), JwtAuthenticationFilter.class);

    return http.build();
  }
}
