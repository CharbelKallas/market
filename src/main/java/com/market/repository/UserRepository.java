package com.market.repository;

import com.market.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findOneByUsername(String username);

    Optional<User> findOneByEmail(String email);

    /** Just the token version, for the cheap per-request JWT check. */
    @Query("select u.tokenVersion from User u where u.id = :id")
    Optional<Integer> findTokenVersionById(@Param("id") Long id);

    /** Records a wrong password, in its own transaction (sign-in itself isn't transactional). */
    @Transactional
    @Modifying
    @Query("update User u set u.failedSigninAttempts = u.failedSigninAttempts + 1 where u.username = :username")
    int incrementFailedSignins(@Param("username") String username);

    /** Locks the account if it has reached the limit, and starts counting again for after the lock. */
    @Transactional
    @Modifying
    @Query("update User u set u.lockedUntil = :lockUntil, u.failedSigninAttempts = 0 "
            + "where u.username = :username and u.failedSigninAttempts >= :maxAttempts")
    int lockIfTooManyFailedSignins(@Param("username") String username, @Param("maxAttempts") int maxAttempts,
                                   @Param("lockUntil") Date lockUntil);

    @Transactional
    @Modifying
    @Query("update User u set u.failedSigninAttempts = 0, u.lockedUntil = null where u.id = :id")
    int resetFailedSignins(@Param("id") Long id);
}
