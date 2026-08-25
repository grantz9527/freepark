package com.freepark.local.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PatternAllowlistRepository
        extends JpaRepository<PatternAllowlist, UUID>, JpaSpecificationExecutor<PatternAllowlist> {

    boolean existsByLotIdAndNameIgnoreCase(UUID lotId, String name);

    boolean existsByLotIdAndNameIgnoreCaseAndIdNot(UUID lotId, String name, UUID id);

    boolean existsByLotIdAndPattern(UUID lotId, String pattern);

    boolean existsByLotIdAndPatternAndIdNot(UUID lotId, String pattern, UUID id);
}
