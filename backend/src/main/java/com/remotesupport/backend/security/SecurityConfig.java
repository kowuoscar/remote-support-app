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
                    // reboot-and-topup-details ticket: anyone who can view a Contract (Manager,
                    // its own Agent, its own Client's Tester) can read its Country's active
                    // Carrier catalog through this Contract-scoped route — Contract ownership
                    // itself is re-checked in ContractCarrierController via FleetAccessGuard,
                    // same shape as every other Contract-scoped resource above. The Country-scoped
                    // /api/carriers routes below stay Agent/Manager only, unchanged.
                    .requestMatchers(HttpMethod.GET, "/api/contracts/*/carriers")
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
                    // client-invoice-submission-and-visibility ticket: sending is the Agent's own
                    // act of submitting their work (spec.md Access control) — a Manager never
                    // sends, so this is narrower than the general build/view matcher below.
                    // Ownership of the specific Contract is re-checked in
                    // ClientInvoiceAccessGuard#requireCanSend, same shape as every other
                    // Contract-scoped resource.
                    .requestMatchers(HttpMethod.POST, "/api/contracts/*/client-invoice/send")
                    .hasRole("AGENT")
                    // Approval is the Manager's act alone (spec.md Access control: "Manager ...
                    // approves") and is addressed by invoice id, under /api/client-invoices/**
                    // below — there is no approve route nested under a Contract.
                    // Attaching a carrier invoice file stays Manager/Agent-only — unchanged from
                    // client-invoice-generation (only a DRAFT ever accepts new files, enforced in
                    // ClientInvoiceController#uploadFile).
                    .requestMatchers(HttpMethod.POST, "/api/contracts/*/client-invoice/files")
                    .hasAnyRole("MANAGER", "AGENT")
                    // Everything else under this Contract's Client Invoice (the main view, the
                    // on-demand PDF, listing/downloading carrier invoice files) is open to any
                    // authenticated role at the matcher level: client-invoice-generation kept a
                    // draft invisible to a Tester by matcher alone (there was no "sent" state a
                    // Tester could ever legitimately reach yet); this ticket adds that state, so
                    // the actual "is this visible to *this* caller" decision now lives in
                    // ClientInvoiceController/ClientInvoiceAccessGuard#requireCanView, same shape
                    // as every other Contract-scoped resource's per-request ownership check.
                    .requestMatchers("/api/contracts/*/client-invoice/**")
                    .authenticated()
                    // agent-standing-amounts-and-invoice-generation ticket: an Agent needs to
                    // view/build their own Agent Invoice (spec.md Access control: "Manager: ...
                    // approves both invoice types" / "Agent: ... authors ... their own Agent
                    // Invoice"), which doesn't fit the Manager-only /api/agents/** rule below.
                    // Ownership of this specific Agent is enforced in
                    // AgentInvoiceController/AgentInvoiceAccessGuard, same shape as Client
                    // Invoice above. Must precede the broader Manager-only matcher so it wins.
                    //
                    // agent-invoice-submission-and-approval ticket adds sending, the Agent's
                    // own act (mirrors Client Invoice's /send matcher); ownership of this
                    // specific Agent is re-checked in AgentInvoiceAccessGuard. The Manager's
                    // override/approve/mark-paid are addressed by invoice id, under
                    // /api/agent-invoices/** below — nothing nested under an Agent.
                    .requestMatchers(HttpMethod.POST, "/api/agents/*/invoice/send")
                    .hasRole("AGENT")
                    .requestMatchers("/api/agents/*/invoice", "/api/agents/*/invoice/**")
                    .authenticated()
                    // Manager-only entity setup (manager-entity-setup ticket): Client, Tester
                    // (nested under /api/clients/{id}/testers), Agent and Contract creation and
                    // listing are all Manager-only; an Agent or Tester request is rejected 403.
                    // This also covers /api/agents/{id}/standing-amounts (Manager-only, ticket
                    // AC: "Standing-amount changes are Manager-only") since it isn't matched
                    // above.
                    // manager-invoice-review-queue spec: the Review Queue and every Client Invoice
                    // and Agent Invoice addressed by its own id are Manager-only. Tenant scoping (an unknown or
                    // other-tenant id is 404) is enforced in the controllers.
                    // agent-maintains-carriers ticket: the Carrier catalog is the Manager's and
                    // the Agents'; a Tester has no access at all (carrier-catalog spec, Access).
                    // Which Country an Agent may touch is checked per request in
                    // CarrierCatalogAccessGuard.
                    .requestMatchers("/api/carriers", "/api/carriers/**")
                    .hasAnyRole("MANAGER", "AGENT")
                    .requestMatchers("/api/review-queue","/api/client-invoices/**", "/api/agent-invoices/**")
                    .hasRole("MANAGER")
                    // manager-approves-requests ticket: the Pending Requests list and a Request's
                    // own approve/reject are Manager-only (spec.md Constraints: "An Agent or
                    // Tester gets 403 on those routes and on the pending list") — a distinct
                    // top-level path from the Contract-scoped /api/contracts/*/requests/** above,
                    // so this doesn't narrow that route's existing access. Tenant scoping (an
                    // unknown or other-tenant id is 404) is enforced in RequestByIdController.
                    .requestMatchers("/api/pending-requests", "/api/requests/**")
                    .hasRole("MANAGER")
                    .requestMatchers("/api/clients/**", "/api/agents/**", "/api/contracts/**")
                    .hasRole("MANAGER")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(new RequestLoggingFilter(), JwtAuthenticationFilter.class);

    return http.build();
  }
}
