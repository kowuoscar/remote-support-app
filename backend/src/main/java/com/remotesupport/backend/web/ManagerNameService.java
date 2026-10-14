package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.ManagerName;
import com.remotesupport.backend.domain.Role;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes and reads the caller's own Manager name ({@code users.name}). The rule for a valid name
 * lives in {@link ManagerName}; this service applies it, resolves the row from the principal
 * (never from the request) and owns the transaction.
 */
@Service
public class ManagerNameService {

  private final UserRepository userRepository;

  public ManagerNameService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /** Stores the normalised {@code rawName} on the caller's own row; throws {@link ManagerName.Refused}. */
  @Transactional
  public void rename(AuthenticatedPrincipal principal, String rawName) {
    ManagerName name = ManagerName.of(rawName);
    userRepository
        .findById(principal.userId())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Authenticated user " + principal.userId() + " has no User row"))
        .setName(name.value());
  }

  /** The caller's Manager name, read from the row each call; empty for a non-Manager or unset. */
  @Transactional(readOnly = true)
  public Optional<String> nameOf(AuthenticatedPrincipal principal) {
    if (!Role.MANAGER.name().equals(principal.role())) {
      return Optional.empty();
    }
    return userRepository.findById(principal.userId()).map(User::getName);
  }
}
