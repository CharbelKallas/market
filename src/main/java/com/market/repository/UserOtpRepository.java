package com.market.repository;

import com.market.model.user.UserOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

public interface UserOtpRepository extends JpaRepository<UserOtp, Long> {
    Optional<UserOtp> findOneByUserId(Long id);

    /**
     * Puts a code back exactly as it was before a resend that reached nobody, so the failed resend changes nothing:
     * not the code, the guess counter, the cooldown or the daily count. Called after the resend's transaction
     * committed, so it needs a transaction of its own.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query("update UserOtp o set o.otp = :code, o.expiryDate = :expiryDate, o.sentDate = :sentDate, "
            + "o.failedAttempts = :failedAttempts, o.resendCount = :resendCount where o.id = :id")
    int restore(@Param("id") Long id, @Param("code") String code, @Param("expiryDate") Date expiryDate,
                @Param("sentDate") Date sentDate, @Param("failedAttempts") int failedAttempts,
                @Param("resendCount") int resendCount);
}
