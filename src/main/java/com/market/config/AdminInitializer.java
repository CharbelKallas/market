package com.market.config;

import com.market.model.user.Role;
import com.market.model.user.User;
import com.market.model.user.UserRole;
import com.market.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Sign-up only ever creates regular users, so this creates the first admin from
 * ADMIN_USERNAME / ADMIN_PASSWORD (and optional ADMIN_EMAIL) when they are set.
 */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final String username;

    private final String password;

    private final String email;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            @Value("${app.admin.username:}") String username,
                            @Value("${app.admin.password:}") String password,
                            @Value("${app.admin.email:}") String email) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.email = email;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password) || userRepository.existsByUsername(username))
            return;

        User admin = new User()
                .setUsername(username)
                .setPassword(passwordEncoder.encode(password))
                .setEmail(StringUtils.hasText(email) ? email : null)
                .setFirstName("Admin")
                .setLastName("Admin")
                .setVerifiedDate(new Date());
        admin.setUserRoles(new HashSet<>(Set.of(
                new UserRole().setName(Role.ROLE_ADMIN).setUser(admin),
                new UserRole().setName(Role.ROLE_USER).setUser(admin))));

        userRepository.save(admin);
        log.info("Created admin user '{}'", username);
    }
}
