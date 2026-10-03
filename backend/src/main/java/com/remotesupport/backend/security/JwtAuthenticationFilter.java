package com.remotesupport.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the {@code Authorization: Bearer <token>} header on every request and, when the
 * token is valid, populates the {@link SecurityContextHolder} with the identity it carries.
 * Absent or invalid tokens simply leave the context unauthenticated, so downstream
 * {@code authorizeHttpRequests} rules reject them with 401.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;

  private final LoginState loginState;

  public JwtAuthenticationFilter(JwtService jwtService, LoginState loginState) {
    this.jwtService = jwtService;
    this.loginState = loginState;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    extractToken(request)
        .flatMap(jwtService::parse)
        // A token outlives a deactivation: the Login must still exist and be active, read afresh
        // on every request (deactivate-a-login, door 2).
        .filter(principal -> loginState.isActive(principal.userId()))
        .ifPresent(
            principal -> {
              var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()));
              var authentication =
                  new UsernamePasswordAuthenticationToken(principal, null, authorities);
              SecurityContextHolder.getContext().setAuthentication(authentication);
            });

    filterChain.doFilter(request, response);
  }

  private Optional<String> extractToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return Optional.of(header.substring(BEARER_PREFIX.length()));
    }
    return Optional.empty();
  }
}
