package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Users are looked up by username for authentication, and by id for token re-validation. */
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByUsername(String username);
}
