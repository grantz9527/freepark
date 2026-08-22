package com.freepark.local.operator;

import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.UserRole;

@Service
public class OperatorService {

    private final LocalUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public OperatorService(LocalUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<OperatorView> listOperators(UUID requesterId) {
        requireAdmin(requesterId);
        return users.findAllByRoleOrderByCreatedAtDesc(UserRole.OPERATOR).stream()
                .map(OperatorView::from)
                .toList();
    }

    @Transactional
    public OperatorView createOperator(UUID requesterId, CreateOperatorRequest request) {
        requireAdmin(requesterId);
        String username = request.username().trim();
        if (users.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        LocalUser operator = new LocalUser(
                username,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                UserRole.OPERATOR);
        return OperatorView.from(users.save(operator));
    }

    private void requireAdmin(UUID userId) {
        LocalUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
