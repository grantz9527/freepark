package com.freepark.local.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalUserRepository extends JpaRepository<LocalUser, UUID> {

    Optional<LocalUser> findByUsername(String username);
}
