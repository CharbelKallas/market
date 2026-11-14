package com.market.service;

import com.market.exception.MarketException;
import com.market.model.user.User;
import com.market.model.user.UserOtp;
import com.market.repository.UserOtpRepository;
import com.market.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Optional;

/**
 * Everything about one-time verification codes: issuing, sending, verifying and the limits that make them hard to
 * brute-force (5 guesses per code, one resend a minute, 5 resends a day).
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    static final int MAX_ATTEMPTS = 5;

    static final long RESEND_COOLDOWN_MS = 60_000;

    static final int MAX_RESENDS_PER_DAY = 5;

    private static final long DAY_MS = 24 * 60 * 60 * 1000L;

    private final UserRepository userRepository;

    private final UserOtpRepository otpRepository;

    private final MessageService messageService;

    private final long expirationMs;

    private final int size;

    public OtpService(UserRepository userRepository, UserOtpRepository otpRepository, MessageService messageService,
                      @Value("${app.otpExpirationMs}") long expirationMs,
                      @Value("${otp.size}") int size) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.messageService = messageService;
        this.expirationMs = expirationMs;
        this.size = size;
    }

    /** A new code for a user being created; saved with the user. Call {@link #sendAfterCommit} once it is saved. */
    public UserOtp newOtp(User user) {
        Date now = new Date();
        return new UserOtp()
                .setUser(user)
                .setSentDate(now)
                .setExpiryDate(new Date(now.getTime() + expirationMs))
                .setOtp(generateCode());
    }

    /** Sends a sign-up code after commit. A failure is only logged: the account exists and the user can resend. */
    public void sendAfterCommit(User user, String code) {
        afterCommit(user, code, () -> { });
    }

    public boolean hasPendingOtp(User user) {
        Date now = new Date();
        return user.getUserOtps() != null && user.getUserOtps().stream()
                .anyMatch(otp -> otp.getExpiryDate().after(now));
    }

    @Transactional
    public boolean verify(Long userId, String code) {
        Optional<UserOtp> found = otpRepository.findForUpdateByUserId(userId);
        if (found.isEmpty())
            return false;

        UserOtp userOtp = found.get();
        // A locked-out code is kept (not deleted) so its sent date still enforces the resend cooldown.
        if (userOtp.getExpiryDate().before(new Date()) || userOtp.getFailedAttempts() >= MAX_ATTEMPTS)
            return false;

        if (!MessageDigest.isEqual(userOtp.getOtp().getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8))) {
            // Returning (not throwing) keeps the transaction, so the failed attempt is saved.
            userOtp.setFailedAttempts(userOtp.getFailedAttempts() + 1);
            return false;
        }

        User user = userOtp.getUser();
        user.setVerifiedDate(new Date());
        user.getUserOtps().remove(userOtp);
        otpRepository.delete(userOtp);
        return true;
    }

    /**
     * Sends a new code if the user exists, is still unverified, and is within the cooldown and daily limits. In every
     * other case it silently does nothing, so the public endpoint gives the same answer whether an account exists,
     * is verified or is pending. Only a delivery failure is reported (503), since that only happens after a real send.
     */
    @Transactional
    public void resend(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getVerifiedDate() != null)
            return;

        // Users always have a code until they verify (locked-out codes are kept), so there is one to update.
        UserOtp otp = otpRepository.findForUpdateByUserId(userId).orElse(null);
        if (otp == null)
            return;
        Date now = new Date();
        if (otp.getSentDate() != null && now.getTime() - otp.getSentDate().getTime() < RESEND_COOLDOWN_MS)
            return;

        if (otp.getResendWindowStart() == null || now.getTime() - otp.getResendWindowStart().getTime() >= DAY_MS)
            otp.setResendWindowStart(now).setResendCount(0);
        // Each resend resets the guess limit, so resends themselves must be limited.
        if (otp.getResendCount() >= MAX_RESENDS_PER_DAY)
            return;

        // Kept so a resend that reaches nobody can be undone completely, guess counter included.
        Snapshot before = new Snapshot(otp);
        otp.setExpiryDate(new Date(now.getTime() + expirationMs))
                .setSentDate(now)
                .setFailedAttempts(0)
                .setResendCount(otp.getResendCount() + 1)
                .setOtp(generateCode());
        UserOtp saved = otpRepository.save(otp);

        afterCommit(user, saved.getOtp(), () -> {
            otpRepository.restore(saved.getId(), saved.getOtp(), before.code, before.expiryDate, before.sentDate,
                    before.failedAttempts, before.resendCount);
            throw MarketException.serviceUnavailable("Could not send the OTP. Please try again later.", 60);
        });
    }

    /**
     * Sends the code once the transaction has committed, so no DB connection is held during the SMTP/SMS call and
     * nobody receives a code that was rolled back. onUndelivered runs only if a channel failed and no channel
     * delivered it: if the email arrived, the resend counts even when the SMS failed.
     */
    private void afterCommit(User user, String code, Runnable onUndelivered) {
        String email = user.getEmail();
        String mobileNumber = user.getMobileNumber();
        Long userId = user.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                boolean delivered = false;
                boolean failed = false;
                try {
                    delivered |= messageService.sendEmailMessage(email, "OTP verification", "Your OTP is : " + code);
                } catch (RuntimeException e) {
                    failed = true;
                    log.error("Could not send the OTP email to user {}", userId, e);
                }
                try {
                    delivered |= messageService.sendSmsMessage(mobileNumber, "Your OTP is : " + code);
                } catch (RuntimeException e) {
                    failed = true;
                    log.error("Could not send the OTP SMS to user {}", userId, e);
                }
                if (failed && !delivered)
                    onUndelivered.run();
            }
        });
    }

    private String generateCode() {
        int lowerBound = (int) Math.pow(10, size - 1);
        return String.valueOf(lowerBound + RANDOM.nextInt(9 * lowerBound));
    }

    private static final class Snapshot {
        final String code;
        final Date expiryDate;
        final Date sentDate;
        final int failedAttempts;
        final int resendCount;

        Snapshot(UserOtp otp) {
            this.code = otp.getOtp();
            this.expiryDate = otp.getExpiryDate();
            this.sentDate = otp.getSentDate();
            this.failedAttempts = otp.getFailedAttempts();
            this.resendCount = otp.getResendCount();
        }
    }
}
