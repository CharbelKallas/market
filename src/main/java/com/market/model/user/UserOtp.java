package com.market.model.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "\"user_otp\"")
public class UserOtp {

    @Id
    @Column(name = "user_otp_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String otp;

    @Column(name = "expiry_date")
    private Date expiryDate;

    @Column(name = "sent_date")
    private Date sentDate;

    /** Wrong guesses against this OTP; it stops working once the limit is reached. */
    @Column(name = "failed_attempts", nullable = false, columnDefinition = "integer not null default 0")
    private int failedAttempts;

    /** Resends within the current 24-hour window, capped so the guess limit can't be reset forever. */
    @Column(name = "resend_count", nullable = false, columnDefinition = "integer not null default 0")
    private int resendCount;

    @Column(name = "resend_window_start")
    private Date resendWindowStart;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}
