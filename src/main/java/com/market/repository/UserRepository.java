package com.market.repository;

import com.market.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findOneByUsername(String username);

    Optional<User> findOneByEmail(String email);

    /** Just the token version, for the cheap per-request JWT check. */
    @Query("select u.tokenVersion from User u where u.id = :id")
    Optional<Integer> findTokenVersionById(@Param("id") Long id);

}
