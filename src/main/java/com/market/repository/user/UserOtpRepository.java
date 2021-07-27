package com.market.repository.user;

import com.market.model.user.UserOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserOtpRepository extends JpaRepository<UserOtp, Long> {
    Optional<UserOtp> findOneByUserIdAndOtp(Long id, String otp);
}
