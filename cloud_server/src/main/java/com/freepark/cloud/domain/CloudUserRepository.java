package com.freepark.cloud.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CloudUserRepository extends JpaRepository<CloudUser, java.util.UUID> {

    Optional<CloudUser> findByUsername(String username);
}
