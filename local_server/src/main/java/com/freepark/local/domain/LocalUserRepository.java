package com.freepark.local.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalUserRepository extends JpaRepository<LocalUser, UUID> {

    Optional<LocalUser> findByUsername(String username);

    boolean existsByUsername(String username);

    List<LocalUser> findAllByRoleOrderByCreatedAtDesc(UserRole role);
}
