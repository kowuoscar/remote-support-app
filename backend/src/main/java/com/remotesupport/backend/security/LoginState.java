package com.remotesupport.backend.security;

import com.remotesupport.backend.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Whether a Login is still active: the per-request check behind {@link JwtAuthenticationFilter}.
 * A primary-key read with no cache, so a deactivation takes effect on the very next request.
 */
@Component
public class LoginState {

  private final UserRepository userRepository;

  public LoginState(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /** {@code false} for a deactivated Login and for a {@code userId} with no row. */
  public boolean isActive(UUID userId) {
    return userRepository.existsByIdAndDeactivatedAtIsNull(userId);
  }
}
