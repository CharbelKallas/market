package com.market.repository;

import com.market.model.user.UserOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UserOtpRepository extends JpaRepository<UserOtp, Long> {
    Optional<UserOtp> findOneByUserId(Long id);

    /**
     * Gives back a resend whose message could not be sent, so it counts neither toward the cooldown nor the cap.
     * Called after the resend's transaction committed, so it needs a transaction of its own.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query("update UserOtp o set o.resendCount = o.resendCount - 1, o.sentDate = null where o.id = :id")
    int undoResend(@Param("id") Long id);
}
