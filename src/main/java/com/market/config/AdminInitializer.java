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
import java.util.Optional;
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
        this.username = User.normalizeIdentifier(username);
        this.password = password;
        this.email = User.normalizeIdentifier(email);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password))
            return;

        Optional<User> existing = userRepository.findOneByUsername(username);
        if (existing.isPresent()) {
            User user = existing.get();
            boolean isAdmin = user.getUserRoles().stream().anyMatch(role -> role.getName() == Role.ROLE_ADMIN);
            if (!isAdmin) {
                log.warn("ADMIN_USERNAME '{}' belongs to an existing non-admin user, so no admin was created. "
                        + "Choose another ADMIN_USERNAME.", username);
            } else if (!passwordEncoder.matches(password, user.getPassword())) {
                // ADMIN_PASSWORD was changed (for example after a leak): apply it and sign out old sessions.
                user.setPassword(passwordEncoder.encode(password))
                        .setTokenVersion(user.getTokenVersion() + 1);
                log.info("Updated the password of admin user '{}' from ADMIN_PASSWORD", username);
            }
            return;
        }

        String adminEmail = StringUtils.hasText(email) ? email : null;
        if (adminEmail != null && userRepository.findOneByEmail(adminEmail).isPresent()) {
            log.warn("ADMIN_EMAIL '{}' already belongs to another user; creating admin '{}' without an email.",
                    adminEmail, username);
            adminEmail = null;
        }

        User admin = new User()
                .setUsername(username)
                .setPassword(passwordEncoder.encode(password))
                .setEmail(adminEmail)
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
