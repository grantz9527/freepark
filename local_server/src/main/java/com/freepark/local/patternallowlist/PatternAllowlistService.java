package com.freepark.local.patternallowlist;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.api.PageView;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.PatternAllowlist;
import com.freepark.local.domain.PatternAllowlistRepository;
import com.freepark.local.domain.UserRole;

import jakarta.persistence.criteria.Predicate;

@Service
public class PatternAllowlistService {

    private final ParkingLotRepository lots;
    private final PatternAllowlistRepository entries;
    private final LocalUserRepository users;

    public PatternAllowlistService(
            ParkingLotRepository lots,
            PatternAllowlistRepository entries,
            LocalUserRepository users) {
        this.lots = lots;
        this.entries = entries;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageView<PatternAllowlistView> listEntries(UUID lotId, String keyword, int page, int size) {
        requireLot(lotId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String trimmed = keyword == null ? null : keyword.trim();
        Specification<PatternAllowlist> spec = buildSpec(lotId, trimmed);
        Page<PatternAllowlist> result = entries.findAll(spec, PageRequest.of(safePage, safeSize));
        return new PageView<>(
                result.getContent().stream().map(PatternAllowlistView::from).toList(),
                result.getTotalElements(),
                safePage,
                safeSize);
    }

    @Transactional
    public PatternAllowlistView createEntry(UUID requesterId, UUID lotId, CreatePatternAllowlistRequest request) {
        requireAdmin(requesterId);
        ParkingLot lot = requireLot(lotId);
        String name = request.name().trim();
        String pattern = validatePattern(request.pattern());
        if (entries.existsByLotIdAndNameIgnoreCase(lotId, name)) {
            throw new BusinessException(ErrorCode.PATTERN_ALLOWLIST_NAME_EXISTS);
        }
        if (entries.existsByLotIdAndPattern(lotId, pattern)) {
            throw new BusinessException(ErrorCode.PATTERN_ALLOWLIST_PATTERN_EXISTS);
        }
        boolean enabled = request.enabled() == null || request.enabled();
        PatternAllowlist entry = new PatternAllowlist(
                lot, name, pattern, normalizeOptional(request.remark()), enabled);
        return PatternAllowlistView.from(entries.save(entry));
    }

    @Transactional
    public PatternAllowlistView updateEntry(
            UUID requesterId, UUID lotId, UUID entryId, UpdatePatternAllowlistRequest request) {
        requireAdmin(requesterId);
        requireLot(lotId);
        PatternAllowlist entry = entries.findById(entryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!entry.getLot().getId().equals(lotId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        String name = request.name().trim();
        String pattern = validatePattern(request.pattern());
        if (entries.existsByLotIdAndNameIgnoreCaseAndIdNot(lotId, name, entryId)) {
            throw new BusinessException(ErrorCode.PATTERN_ALLOWLIST_NAME_EXISTS);
        }
        if (entries.existsByLotIdAndPatternAndIdNot(lotId, pattern, entryId)) {
            throw new BusinessException(ErrorCode.PATTERN_ALLOWLIST_PATTERN_EXISTS);
        }
        boolean enabled = request.enabled() == null ? entry.isEnabled() : request.enabled();
        entry.updateDetails(name, pattern, normalizeOptional(request.remark()), enabled);
        return PatternAllowlistView.from(entries.save(entry));
    }

    @Transactional
    public void deleteEntry(UUID requesterId, UUID lotId, UUID entryId) {
        requireAdmin(requesterId);
        requireLot(lotId);
        PatternAllowlist entry = entries.findById(entryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!entry.getLot().getId().equals(lotId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        entries.delete(entry);
    }

    private Specification<PatternAllowlist> buildSpec(UUID lotId, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("lot").get("id"), lotId));
            if (keyword != null && !keyword.isEmpty()) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("pattern")), like),
                        cb.like(cb.lower(root.get("remark")), like)));
            }
            query.orderBy(cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String validatePattern(String raw) {
        String pattern = raw.trim();
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException ex) {
            throw new BusinessException(ErrorCode.PATTERN_ALLOWLIST_INVALID_PATTERN);
        }
        return pattern;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ParkingLot requireLot(UUID lotId) {
        return lots.findById(lotId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void requireAdmin(UUID userId) {
        LocalUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
