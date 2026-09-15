package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Client;
import com.remotesupport.backend.dto.ClientCreateRequest;
import com.remotesupport.backend.dto.ClientResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.ClientRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.TenantRepository;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager-only Client CRUD (create + list). Authorization is enforced at the request-matcher
 * level in {@link com.remotesupport.backend.security.SecurityConfig}, not per-method here, since
 * every endpoint on this controller is Manager-only.
 */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

  private final ClientRepository clientRepository;
  private final TesterRepository testerRepository;
  private final ContractRepository contractRepository;
  private final TenantRepository tenantRepository;

  public ClientController(
      ClientRepository clientRepository,
      TesterRepository testerRepository,
      ContractRepository contractRepository,
      TenantRepository tenantRepository) {
    this.clientRepository = clientRepository;
    this.testerRepository = testerRepository;
    this.contractRepository = contractRepository;
    this.tenantRepository = tenantRepository;
  }

  @PostMapping
  public ResponseEntity<ClientResponse> create(
      @Valid @RequestBody ClientCreateRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Client client = new Client();
    client.setId(UUID.randomUUID());
    client.setTenant(tenantRepository.getReferenceById(principal.tenantId()));
    client.setName(request.name());
    client.setCreatedAt(Instant.now());
    clientRepository.save(client);

    AuditLog.created("Client", client.getId(), principal.userId(), principal.tenantId());

    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(client));
  }

  @GetMapping
  public List<ClientResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return clientRepository.findByTenantIdOrderByNameAsc(principal.tenantId()).stream()
        .map(this::toResponse)
        .toList();
  }

  private ClientResponse toResponse(Client client) {
    String primaryContactUsername =
        testerRepository.findByClientIdOrderByCreatedAtAsc(client.getId()).stream()
            .filter(tester -> tester.isPrimaryContact())
            .findFirst()
            .map(tester -> tester.getUser().getUsername())
            .orElse(null);
    long contractCount = contractRepository.countByClientId(client.getId());
    return ClientResponse.of(client, primaryContactUsername, contractCount);
  }
}
