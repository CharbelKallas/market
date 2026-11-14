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
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}
