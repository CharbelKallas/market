package com.market.repository.user;

import com.market.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findOneByUsernameAndVerifiedDateIsNotNull(String username);

    Optional<User> findOneByUsername(String username);

    Optional<User> findOneByEmail(String email);
}
