package com.market.repository.user;

import com.market.model.user.User;
import com.market.model.user.UserOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.Optional;

public interface UserOtpRepository extends JpaRepository<UserOtp, Long> {
    Optional<UserOtp> findOneByUserIdAndOtpAndExpiryDateGreaterThan(Long id, String otp, Date expiryDate);

    Optional<UserOtp> findOneByUserId(Long id);
}
