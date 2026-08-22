package com.freepark.local.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.UserRole;

@Component
public class AdminUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final LocalUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final FreeparkProperties properties;

    public AdminUserInitializer(LocalUserRepository users, PasswordEncoder passwordEncoder,
            FreeparkProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        FreeparkProperties.Admin admin = properties.admin();
        LocalUser user = new LocalUser(
                admin.username().trim(),
                passwordEncoder.encode(admin.password()),
                admin.displayName(),
                UserRole.ADMIN);
        users.save(user);
        log.info("Created default local admin user '{}'", admin.username());
    }
}
