package com.remotesupport.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and validates the JWTs that carry a user's identity, tenant and role across requests.
 * The token is the sole auth mechanism: no server-side session is kept.
 */
@Component
public class JwtService {

  private static final String CLAIM_TENANT_ID = "tenantId";
  private static final String CLAIM_ROLE = "role";

  private final SecretKey signingKey;
  private final long expirationMinutes;

  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationMinutes = expirationMinutes;
  }

  public String issueToken(UUID userId, String username, UUID tenantId, String role) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(username)
        .id(userId.toString())
        .claim(CLAIM_TENANT_ID, tenantId.toString())
        .claim(CLAIM_ROLE, role)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
        .signWith(signingKey)
        .compact();
  }

  /** Returns the decoded claims for a valid, unexpired token, or empty for any other token. */
  public Optional<AuthenticatedPrincipal> parse(String token) {
    try {
      Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
      return Optional.of(
          new AuthenticatedPrincipal(
              UUID.fromString(claims.getId()),
              claims.getSubject(),
              UUID.fromString(claims.get(CLAIM_TENANT_ID, String.class)),
              claims.get(CLAIM_ROLE, String.class)));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** The identity carried by a validated token: user, tenant and role, ready to authorize with. */
  public record AuthenticatedPrincipal(UUID userId, String username, UUID tenantId, String role) {}
}
