package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Client;
import com.remotesupport.backend.domain.Role;
import com.remotesupport.backend.domain.Tester;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.dto.TesterCreateRequest;
import com.remotesupport.backend.dto.TesterResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.ClientRepository;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager-only Tester CRUD (create + list), nested under the owning Client: a Tester is a login
 * (a {@link User} with role {@code TESTER}) that belongs to exactly one {@link Client}. Lives on
 * a Client detail view, not a top-level list (manager-entity-setup ticket).
 */
@RestController
@RequestMapping("/api/clients/{clientId}/testers")
public class TesterController {

  // V1's Tenant-scoped users unique constraint and V55's global unique index, as Postgres names
  // them in a violation — how a conflict caught at flush time (the human's requirement: this
  // stays reachable, and refused 409, even though the pre-check below makes it unreachable in
  // ordinary use; globally-unique-usernames spec.md "Decisions taken") is told apart. A caller
  // must not be able to tell which of the two fired, so both map to the same USERNAME_TAKEN
  // reason, matching AgentLoginService's own violation-name matching.
  private static final String TENANT_USERNAME_CONSTRAINT = "uq_users_tenant_username";
  private static final String GLOBAL_USERNAME_CONSTRAINT = "uq_users_username_global";

  private final ClientRepository clientRepository;
  private final TesterRepository testerRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public TesterController(
      ClientRepository clientRepository,
      TesterRepository testerRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.clientRepository = clientRepository;
    this.testerRepository = testerRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @PostMapping
  public ResponseEntity<TesterResponse> create(
      @PathVariable UUID clientId,
      @Valid @RequestBody TesterCreateRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Client client =
        clientRepository
            .findByIdAndTenantId(clientId, principal.tenantId())
            .orElseThrow(() -> new NotFoundException("No client with id " + clientId));

    if (request.isPrimaryContact()
        && testerRepository.existsByClientIdAndPrimaryContactTrue(clientId)) {
      throw new TesterConflictException(
          TesterConflictException.Reason.PRIMARY_CONTACT_EXISTS,
          "Client " + clientId + " already has a primary contact");
    }

    String username = request.username().strip();
    if (userRepository.existsByUsernameNormalized(username)) {
      throw usernameTaken();
    }

    User user = new User();
    user.setId(UUID.randomUUID());
    user.setTenant(client.getTenant());
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setRole(Role.TESTER);
    user.setCreatedAt(Instant.now());
    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      String detail = String.valueOf(e.getMostSpecificCause().getMessage());
      if (detail.contains(TENANT_USERNAME_CONSTRAINT) || detail.contains(GLOBAL_USERNAME_CONSTRAINT)) {
        throw usernameTaken();
      }
      throw e;
    }

    Tester tester = new Tester();
    tester.setId(UUID.randomUUID());
    tester.setTenant(client.getTenant());
    tester.setClient(client);
    tester.setUser(user);
    tester.setPrimaryContact(request.isPrimaryContact());
    tester.setCreatedAt(Instant.now());
    testerRepository.save(tester);

    AuditLog.created("Tester", tester.getId(), principal.userId(), principal.tenantId());

    return ResponseEntity.status(HttpStatus.CREATED).body(TesterResponse.of(tester));
  }

  @GetMapping
  public List<TesterResponse> list(
      @PathVariable UUID clientId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    clientRepository
        .findByIdAndTenantId(clientId, principal.tenantId())
        .orElseThrow(() -> new NotFoundException("No client with id " + clientId));

    return testerRepository.findByClientIdOrderByCreatedAtAsc(clientId).stream()
        .map(TesterResponse::of)
        .toList();
  }

  private static TesterConflictException usernameTaken() {
    return new TesterConflictException(
        TesterConflictException.Reason.USERNAME_TAKEN,
        "That email is already in use. Choose another one and try again.");
  }

  /**
   * Both create-conflict 409s carry a {@code code} ({@code USERNAME_TAKEN} or {@code
   * PRIMARY_CONTACT_EXISTS}) so the dialog can tell "pick another email" from "this Client
   * already has a primary contact" apart — the asymmetry globally-unique-usernames spec.md fixes
   * ("The Tester path's asymmetry is fixed here, not left"), the same shape {@link
   * AgentLoginConflictException} already gives the Agent path.
   */
  @ExceptionHandler(TesterConflictException.class)
  public ResponseEntity<Map<String, String>> conflict(TesterConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", e.reason().name(), "message", e.getMessage()));
  }

  /** A 409 from creating a Tester, carrying which of its two causes applies. */
  static class TesterConflictException extends ConflictException {

    /** The machine-readable cause, sent to the client as {@code code}. */
    enum Reason {
      USERNAME_TAKEN,
      PRIMARY_CONTACT_EXISTS
    }

    private final Reason reason;

    TesterConflictException(Reason reason, String message) {
      super(message);
      this.reason = reason;
    }

    Reason reason() {
      return reason;
    }
  }
}
