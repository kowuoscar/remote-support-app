package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Request;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRepository extends JpaRepository<Request, UUID> {

  List<Request> findByContractIdOrderByCreatedAtAsc(UUID contractId);
}
