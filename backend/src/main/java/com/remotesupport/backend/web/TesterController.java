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
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
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
      throw new ConflictException("Client " + clientId + " already has a primary contact");
    }

    User user = new User();
    user.setId(UUID.randomUUID());
    user.setTenant(client.getTenant());
    user.setUsername(request.username());
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setRole(Role.TESTER);
    user.setCreatedAt(Instant.now());
    try {
      userRepository.save(user);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("Username " + request.username() + " is already in use");
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
}
