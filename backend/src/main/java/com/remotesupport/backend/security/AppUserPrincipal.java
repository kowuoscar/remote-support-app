package com.remotesupport.backend.security;

import com.remotesupport.backend.domain.User;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security's view of an authenticated {@link User}: adds the tenant id and a single
 * role-derived authority (e.g. {@code ROLE_MANAGER}) on top of the standard contract.
 */
public class AppUserPrincipal implements UserDetails {

  private final UUID userId;
  private final UUID tenantId;
  private final String username;
  private final String passwordHash;
  private final String role;

  public AppUserPrincipal(User user) {
    this.userId = user.getId();
    this.tenantId = user.getTenant().getId();
    this.username = user.getUsername();
    this.passwordHash = user.getPasswordHash();
    this.role = user.getRole().name();
  }

  public UUID userId() {
    return userId;
  }

  public UUID tenantId() {
    return tenantId;
  }

  public String role() {
    return role;
  }

  @Override
  public List<GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return username;
  }
}
