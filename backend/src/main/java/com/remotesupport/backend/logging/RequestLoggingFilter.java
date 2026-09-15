package com.remotesupport.backend.logging;

import com.remotesupport.backend.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Structured request logging: every request logs its method, path and outcome status, carrying
 * the authenticated user's tenant and user id in the MDC (see {@code logging.pattern.console} in
 * application.yml) whenever a request is authenticated. Wired into the Spring Security filter
 * chain (not auto-registered as a generic servlet filter) immediately after
 * {@code JwtAuthenticationFilter}, so the security context is already populated when it runs.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
  private static final String MDC_TENANT_ID = "tenantId";
  private static final String MDC_USER_ID = "userId";

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    try {
      populateMdc();
      filterChain.doFilter(request, response);
      log.info("{} {} -> {}", request.getMethod(), request.getRequestURI(), response.getStatus());
    } finally {
      MDC.remove(MDC_TENANT_ID);
      MDC.remove(MDC_USER_ID);
    }
  }

  private void populateMdc() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof JwtService.AuthenticatedPrincipal principal) {
      MDC.put(MDC_TENANT_ID, principal.tenantId().toString());
      MDC.put(MDC_USER_ID, principal.userId().toString());
    }
  }
}
