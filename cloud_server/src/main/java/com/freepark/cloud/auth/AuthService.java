package com.freepark.cloud.auth;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.cloud.common.exception.BusinessException;
import com.freepark.cloud.common.exception.ErrorCode;
import com.freepark.cloud.domain.CloudUser;
import com.freepark.cloud.domain.CloudUserRepository;

@Service
public class AuthService {

    private final CloudUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(CloudUserRepository users, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        CloudUser user = users.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        String token = jwtTokenService.createToken(user);
        return new LoginResponse(token, "Bearer", jwtTokenService.expiresInSeconds(), UserView.from(user));
    }

    @Transactional(readOnly = true)
    public UserView currentUser(UUID userId) {
        CloudUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return UserView.from(user);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        CloudUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.WRONG_PASSWORD);
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
    }
}
