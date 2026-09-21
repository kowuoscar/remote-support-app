package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Client;
import com.remotesupport.backend.domain.Role;
import com.remotesupport.backend.domain.Tester;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.web.TesterConflictException.Reason;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a Tester's login: a {@code TESTER}-role {@link User} linked to the {@link Tester} row
 * that belongs to it, in the same shape {@link AgentLoginService} already gives the Agent path
 * (globally-unique-usernames spec.md review, finding F5 — the global-uniqueness pre-check and the
 * flush-time constraint-name branching are business rules, and belong here, not in {@link
 * TesterController}).
 *
 * <p>{@code @Transactional} on {@link #create}, so the {@link User} and {@link Tester} inserts
 * commit or roll back together — a failure between them can no longer leave an orphan login row
 * (finding F10, fixed as a consequence of moving this write into one service method rather than
 * leaving it uncoordinated in the controller).
 */
@Service
public class TesterLoginService {

  // V1's Tenant-scoped users unique constraint and V55's global unique index, as Postgres names
  // them in a violation — how a conflict caught at flush time (the human's requirement: this
  // stays reachable, and refused 409, even though the pre-check below makes it unreachable in
  // ordinary use; globally-unique-usernames spec.md "Decisions taken") is told apart. A caller
  // must not be able to tell which of the two fired, so both map to the same USERNAME_TAKEN
  // reason, matching AgentLoginService's own violation-name matching.
  private static final String TENANT_USERNAME_CONSTRAINT = "uq_users_tenant_username";
  private static final String GLOBAL_USERNAME_CONSTRAINT = "uq_users_username_global";

  private final UserRepository userRepository;
  private final TesterRepository testerRepository;
  private final PasswordEncoder passwordEncoder;

  public TesterLoginService(
      UserRepository userRepository,
      TesterRepository testerRepository,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.testerRepository = testerRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Writes the {@link User} and the {@link Tester} that points at it as one transaction. Throws
   * {@link TesterConflictException} (409, {@code USERNAME_TAKEN}) when the username is already
   * taken anywhere in the deployment — the global, case- and trim-insensitive pre-check
   * (globally-unique-usernames spec.md "One rule, five creation paths") — or, for a race the
   * pre-check cannot see, when the flush itself is rejected by either username constraint. The
   * primary-contact conflict is still the caller's to raise: it depends only on the {@link
   * Client}, before any write this method makes.
   */
  @Transactional
  public Tester create(Client client, String username, String password, boolean primaryContact) {
    if (userRepository.existsByUsernameNormalized(username)) {
      throw usernameTaken();
    }

    User user = new User();
    user.setId(UUID.randomUUID());
    user.setTenant(client.getTenant());
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(Role.TESTER);
    user.setCreatedAt(Instant.now());
    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      throw toConflict(e);
    }

    Tester tester = new Tester();
    tester.setId(UUID.randomUUID());
    tester.setTenant(client.getTenant());
    tester.setClient(client);
    tester.setUser(user);
    tester.setPrimaryContact(primaryContact);
    tester.setCreatedAt(Instant.now());
    testerRepository.save(tester);

    return tester;
  }

  private static RuntimeException toConflict(DataIntegrityViolationException e) {
    String detail = String.valueOf(e.getMostSpecificCause().getMessage());
    if (detail.contains(TENANT_USERNAME_CONSTRAINT) || detail.contains(GLOBAL_USERNAME_CONSTRAINT)) {
      return usernameTaken();
    }
    return e;
  }

  private static TesterConflictException usernameTaken() {
    return new TesterConflictException(
        Reason.USERNAME_TAKEN, "That email is already in use. Choose another one and try again.");
  }
}
