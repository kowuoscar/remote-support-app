package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Client;
import com.remotesupport.backend.domain.Tester;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.domain.Username;
import com.remotesupport.backend.dto.TesterCreateRequest;
import com.remotesupport.backend.dto.TesterResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.ClientRepository;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 *
 * <p>The username-uniqueness rule and the write itself live in {@link TesterLoginService} — a
 * controller translates HTTP <-> domain and never branches on domain state (docs/agents/
 * coding-standards.md Backend rule 2; globally-unique-usernames spec.md review, finding F5). The
 * primary-contact conflict stays here: it is a read of the {@link Client} this controller already
 * looked up, decided before {@link TesterLoginService} is asked to write anything.
 */
@RestController
@RequestMapping("/api/clients/{clientId}/testers")
public class TesterController {

  private final ClientRepository clientRepository;
  private final TesterRepository testerRepository;
  private final TesterLoginService testerLoginService;

  public TesterController(
      ClientRepository clientRepository,
      TesterRepository testerRepository,
      TesterLoginService testerLoginService) {
    this.clientRepository = clientRepository;
    this.testerRepository = testerRepository;
    this.testerLoginService = testerLoginService;
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

    Tester tester =
        testerLoginService.create(
            client,
            Username.trim(request.username()),
            request.password(),
            request.isPrimaryContact());

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
}
