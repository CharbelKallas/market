package com.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.market.config.AdminInitializer;
import com.market.config.ExpectedSqlErrorLogFilter;
import ch.qos.logback.core.spi.FilterReply;
import com.market.exception.CustomExceptionHandler;
import com.market.exception.MarketException;
import com.market.repository.UserOtpRepository;
import com.market.repository.UserRepository;
import com.market.service.ClientAddresses;
import com.market.service.BoundedLruMap;
import com.market.service.LoginAttemptService;
import com.market.service.MessageService;
import com.market.service.SignupRateLimiter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MarketApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @MockBean
    private MessageService messageService;

    @Autowired
    private UserOtpRepository otpRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private Environment environment;

    @Test
    void fullFlow_signupVerifySigninAndPlaceOrder() throws Exception {
        long userId = signup("alice", "alice@example.com");

        // Unverified users can't sign in.
        mvc.perform(post("/api/user/signin").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "alice", "password", "password123"))))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/user/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId, "otp", "0"))))
                .andExpect(status().isBadRequest());

        verifyUser(userId, "alice@example.com");
        String userToken = signin("alice", "password123");

        // Only admins can add items; the bootstrapped admin can.
        MockMultipartFile image = new MockMultipartFile("image", "apple.png", "image/png", PNG);
        mvc.perform(multipart("/api/item/new").file(image).param("itemName", "Apple").param("amount", "1.50")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("ACCESS_DENIED"));

        String adminToken = signin("admin", "admin-password");
        JsonNode item = body(mvc.perform(multipart("/api/item/new").file(image)
                        .param("itemName", "Apple").param("amount", "1.50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn()).path("payload");
        long itemId = item.path("id").asLong();
        assertThat(itemId).isPositive();

        // Item names are unique regardless of case and extra spaces.
        mvc.perform(multipart("/api/item/new").param("itemName", "  APPLE ").param("amount", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/item/all").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content[0].id").value(itemId))
                .andExpect(jsonPath("$.payload.content[0].amount").value(1.5))
                .andExpect(jsonPath("$.payload.content[0].hasImage").value(true));

        mvc.perform(get("/api/item/image").param("itemId", String.valueOf(itemId))
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PNG));

        Map<String, Object> order = Map.of(
                "locationLatitude", 33.89, "locationLongitude", 35.50,
                "items", Map.of(String.valueOf(itemId), 3));
        mvc.perform(post("/api/order/place").contentType(MediaType.APPLICATION_JSON).content(json(order))
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.userId").value(userId))
                .andExpect(jsonPath("$.payload.orderItems[0].amount").value(4.5))
                .andExpect(jsonPath("$.payload.deliveryCharge").value(2.0))
                .andExpect(jsonPath("$.payload.total").value(6.5));
    }

    @Test
    void imageUploadRejectsNonRasterFiles() throws Exception {
        String adminToken = signin("admin", "admin-password");
        MockMultipartFile svg = new MockMultipartFile("image", "x.svg", "image/png",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes());
        mvc.perform(multipart("/api/item/new").file(svg).param("itemName", "Evil").param("amount", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otpStopsWorkingAfterTooManyWrongGuesses() throws Exception {
        long userId = signup("dave", "dave@example.com");
        String otp = capturedOtp("dave@example.com");

        for (int i = 0; i < 5; i++)
            mvc.perform(post("/api/user/verify").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("userId", userId, "otp", "wrong"))))
                    .andExpect(status().isBadRequest());

        mvc.perform(post("/api/user/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId, "otp", otp))))
                .andExpect(status().isBadRequest());

        // A resend right after sign-up is held back by the cooldown: same generic answer, no new code.
        String lockedCode = otpRepository.findOneByUserId(userId).orElseThrow().getOtp();
        mvc.perform(post("/api/user/resend_otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId))))
                .andExpect(status().isOk());
        assertThat(otpRepository.findOneByUserId(userId).orElseThrow().getOtp()).isEqualTo(lockedCode);
    }

    @Test
    void signinDoesNotRevealUnverifiedAccountsWithoutThePassword() throws Exception {
        signup("erin", "erin@example.com");
        mvc.perform(post("/api/user/signin").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "erin", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors.message").value("Wrong username or password"));
        mvc.perform(post("/api/user/signin").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "erin", "password", "password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors.message").value("Account is not verified yet"));
    }

    @Test
    void changePassword_appliesToSignedInUser() throws Exception {
        long userId = signup("bob", "bob@example.com");
        verifyUser(userId, "bob@example.com");
        String token = signin("bob", "password123");

        mvc.perform(post("/api/user/change_password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("oldPassword", "wrong-password", "newPassword", "newpassword123")))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        String newToken = body(mvc.perform(post("/api/user/change_password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("oldPassword", "password123", "newPassword", "newpassword123")))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).path("payload").path("token").asText();

        signin("bob", "newpassword123");

        // Tokens issued before the change no longer work; the one returned by the change does.
        mvc.perform(get("/api/item/all").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/item/all").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void usernamesAndEmailsIgnoreCase() throws Exception {
        long userId = signup("Ivy", "Ivy@Example.com");
        mvc.perform(post("/api/user/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupBody("ivy2", "ivy@example.com"))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/user/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupBody("IVY", "other-ivy@example.com"))))
                .andExpect(status().isConflict());

        verifyUser(userId, "ivy@example.com");
        signin("IVY", "password123");
    }

    @Test
    void signupSucceedsEvenIfTheOtpEmailFails() throws Exception {
        doThrow(new RuntimeException("SMTP down")).when(messageService)
                .sendEmailMessage(eq("jack@example.com"), anyString(), anyString());
        long userId = signup("jack", "jack@example.com");
        assertThat(userId).isPositive();
    }

    @Test
    void adminPasswordFromEnvironmentIsAppliedOnlyWhenItChanges() throws Exception {
        // A dedicated admin, so a failure here can't leave the shared "admin" account changed for other tests.
        runAdminInitializer("rotation-admin", "first-password", "");
        String token = signin("rotation-admin", "first-password");

        // The admin changes their own password; a restart with the same ADMIN_PASSWORD keeps it.
        token = body(mvc.perform(post("/api/user/change_password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("oldPassword", "first-password", "newPassword", "own-password")))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).path("payload").path("token").asText();
        runAdminInitializer("rotation-admin", "first-password", "");
        signin("rotation-admin", "own-password");

        // A new ADMIN_PASSWORD is applied and signs out existing sessions.
        runAdminInitializer("rotation-admin", "second-password", "");
        signin("rotation-admin", "second-password");
        mvc.perform(get("/api/item/all").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signinLocksAfterRepeatedWrongPasswords() throws Exception {
        long userId = signup("liam", "liam@example.com");
        verifyUser(userId, "liam@example.com");

        for (int i = 0; i < 5; i++)
            mvc.perform(post("/api/user/signin").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", "liam", "password", "wrong-password"))))
                    .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/user/signin").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "liam", "password", "password123"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void signinResetsFailureCountAfterSuccess() throws Exception {
        long userId = signup("mia", "mia@example.com");
        verifyUser(userId, "mia@example.com");
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 4; i++)
                mvc.perform(post("/api/user/signin").contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("username", "mia", "password", "wrong-password"))))
                        .andExpect(status().isUnauthorized());
            signin("mia", "password123");
        }
    }

    @Test
    void failedResendIsReportedAndNotCounted() throws Exception {
        long userId = signup("noah", "noah@example.com");
        var otp = otpRepository.findOneByUserId(userId).orElseThrow();
        otpRepository.save(otp.setSentDate(new Date(System.currentTimeMillis() - 120_000)));

        doThrow(new RuntimeException("SMTP down")).when(messageService)
                .sendEmailMessage(eq("noah@example.com"), anyString(), anyString());
        mvc.perform(post("/api/user/resend_otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId))))
                .andExpect(status().isServiceUnavailable());

        // Nothing reached the user, so the resend changed nothing: same code, cooldown, counters.
        var after = otpRepository.findOneByUserId(userId).orElseThrow();
        assertThat(after.getResendCount()).isZero();
        assertThat(after.getOtp()).isEqualTo(otp.getOtp());
        assertThat(after.getSentDate().getTime()).isEqualTo(otp.getSentDate().getTime());
        assertThat(after.getFailedAttempts()).isEqualTo(otp.getFailedAttempts());
    }

    @Test
    void failedResendKeepsTheGuessCounter() throws Exception {
        long userId = signup("nora", "nora@example.com");
        for (int i = 0; i < 5; i++)
            mvc.perform(post("/api/user/verify").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("userId", userId, "otp", "wrong"))))
                    .andExpect(status().isBadRequest());
        var otp = otpRepository.findOneByUserId(userId).orElseThrow();
        otpRepository.save(otp.setSentDate(new Date(System.currentTimeMillis() - 120_000)));

        doThrow(new RuntimeException("SMS down")).when(messageService).sendSmsMessage(anyString(), anyString());
        mvc.perform(post("/api/user/resend_otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId))))
                .andExpect(status().isServiceUnavailable());

        // A resend that reached nobody must not hand out 5 fresh guesses.
        assertThat(otpRepository.findOneByUserId(userId).orElseThrow().getFailedAttempts()).isEqualTo(5);
    }

    @Test
    void resendCountsWhenTheEmailArrivedEvenIfSmsFailed() throws Exception {
        long userId = signup("omar", "omar@example.com");
        var otp = otpRepository.findOneByUserId(userId).orElseThrow();
        otpRepository.save(otp.setSentDate(new Date(System.currentTimeMillis() - 120_000)));

        when(messageService.sendEmailMessage(anyString(), anyString(), anyString())).thenReturn(true);
        doThrow(new RuntimeException("SMS down")).when(messageService).sendSmsMessage(anyString(), anyString());
        mvc.perform(post("/api/user/resend_otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId))))
                .andExpect(status().isOk());

        // The cooldown applies, so the inbox can't be flooded: the next resend sends nothing.
        assertThat(otpRepository.findOneByUserId(userId).orElseThrow().getResendCount()).isEqualTo(1);
        mvc.perform(post("/api/user/resend_otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId))))
                .andExpect(status().isOk());
        assertThat(otpRepository.findOneByUserId(userId).orElseThrow().getResendCount()).isEqualTo(1);
    }

    @Test
    void parallelVerifyGuessesAreAllCounted() throws Exception {
        long userId = signup("pia", "pia@example.com");
        int guesses = 5;
        ExecutorService pool = Executors.newFixedThreadPool(guesses);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < guesses; i++)
                results.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(post("/api/user/verify").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("userId", userId, "otp", "wrong"))));
                }));
            start.countDown();
            for (Future<?> result : results)
                result.get();
        } finally {
            pool.shutdown();
        }
        // Without the row lock, simultaneous guesses overwrite each other's count (lost updates) and more than
        // 5 guesses get evaluated. With it, every guess is counted.
        assertThat(otpRepository.findOneByUserId(userId).orElseThrow().getFailedAttempts()).isEqualTo(guesses);
    }

    @Test
    void signinThrottlingGroupsIpv6NetworksAndCapsEachClient() {
        LoginAttemptService attempts = new LoginAttemptService();
        // Rotating addresses inside one IPv6 /64 doesn't escape the limit.
        for (int i = 1; i <= 5; i++)
            failSignin(attempts, "victim", "2001:db8:1:2::" + i);
        assertThatThrownBy(() -> attempts.beginAttempt("victim", "2001:db8:1:2::ffff"))
                .hasMessageContaining("Too many failed sign-ins");
        attempts.beginAttempt("victim", "2001:db8:1:3::1");

        // Spraying many usernames from one address hits the per-client cap...
        attempts.recordSuccess("colleague", "10.0.0.9");
        for (int i = 0; i < 500; i++)
            failSignin(attempts, "user" + i, "10.0.0.9");
        assertThatThrownBy(() -> attempts.beginAttempt("someone-else", "10.0.0.9"))
                .isInstanceOf(MarketException.class)
                .satisfies(e -> assertThat(((MarketException) e).getRetryAfterSeconds()).isPositive());
        // ...but users who already signed in from that shared address are not locked out with it.
        attempts.beginAttempt("colleague", "10.0.0.9");
    }

    @Test
    void resendAnswersTheSameForUnknownAndVerifiedUsers() throws Exception {
        String generic = "If the account is pending verification, a new code was sent. Codes can be requested once a "
                + "minute, up to 5 times a day.";
        mvc.perform(post("/api/user/resend_otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 987654321))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payload").value(generic));

        long userId = signup("rosa", "rosa@example.com");
        verifyUser(userId, "rosa@example.com");
        mvc.perform(post("/api/user/resend_otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payload").value(generic));
    }

    @Test
    void onlyLoopbackProxiesAreTrustedByDefault() {
        // Guards the backslash escaping in application.properties: a broken regex would silently trust the wrong
        // senders (or none).
        String trusted = environment.getProperty("server.tomcat.remoteip.internal-proxies");
        assertThat(trusted).isNotNull();
        Pattern pattern = Pattern.compile(trusted);
        assertThat(pattern.matcher("127.0.0.1").matches()).isTrue();
        assertThat(pattern.matcher("0:0:0:0:0:0:0:1").matches()).isTrue();
        assertThat(pattern.matcher("172.17.0.1").matches()).isFalse();
        assertThat(pattern.matcher("127a0b0c1").matches()).isFalse();
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone")).isEqualTo("UTC");
    }

    @Test
    void verifyGivesUpOnALockHeldTooLong() throws Exception {
        long userId = signup("sam", "sam@example.com");
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> transactionTemplate.executeWithoutResult(status -> {
            otpRepository.findForUpdateByUserId(userId);
            locked.countDown();
            try {
                release.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        holder.start();
        try {
            locked.await();
            mvc.perform(post("/api/user/verify").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("userId", userId, "otp", "wrong"))))
                    .andExpect(status().isConflict())
                    .andExpect(header().string("Retry-After", "1"))
                    .andExpect(jsonPath("$.status").value("CONFLICT"));
        } finally {
            release.countDown();
            holder.join();
        }
    }

    @Test
    void clientKeyNeverResolvesHostnames() {
        assertThat(ClientAddresses.clientKey("random123.attacker.example")).isEqualTo("random123.attacker.example");
        assertThat(ClientAddresses.clientKey("999.10.20.30")).isEqualTo("999.10.20.30");
        assertThat(ClientAddresses.clientKey("10.1.2.3")).isEqualTo("10.1.2.3");
        assertThat(ClientAddresses.clientKey("2001:db8:1:2::7")).isEqualTo("20010db800010002::/64");
    }

    @Test
    void passwordsMustFitBcryptsLimit() throws Exception {
        Map<String, Object> tooLong = new java.util.HashMap<>(signupBody("quinn", "quinn@example.com"));
        tooLong.put("password", "é".repeat(37)); // 37 characters but 74 UTF-8 bytes
        mvc.perform(post("/api/user/signup").contentType(MediaType.APPLICATION_JSON).content(json(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.details.password").exists());
    }

    @Test
    void signinThrottlingIsPerClientIp() {
        LoginAttemptService attempts = new LoginAttemptService();
        for (int i = 0; i < 5; i++)
            failSignin(attempts, "victim", "10.0.0.1");
        assertThatThrownBy(() -> attempts.beginAttempt("victim", "10.0.0.1"))
                .hasMessageContaining("Too many failed sign-ins");
        // The real user on another network is unaffected.
        attempts.beginAttempt("victim", "192.168.1.20");
    }

    @Test
    void ordersAndUsernamesAreBounded() throws Exception {
        mvc.perform(post("/api/user/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupBody("  ab  ", "short@example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.details.username").exists());
        mvc.perform(post("/api/user/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupBody("john smith", "john@example.com"))))
                .andExpect(status().isBadRequest());
        Map<String, Object> badPhone = new java.util.HashMap<>(signupBody("phil", "phil@example.com"));
        badPhone.put("mobileNumber", "call me");
        mvc.perform(post("/api/user/signup").contentType(MediaType.APPLICATION_JSON).content(json(badPhone)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.details.mobileNumber").exists());

        long userId = signup("olga", "olga@example.com");
        verifyUser(userId, "olga@example.com");
        String token = signin("olga", "password123");
        Map<String, Integer> items = new java.util.HashMap<>();
        for (int i = 1; i <= 101; i++)
            items.put(String.valueOf(i), 1);
        mvc.perform(post("/api/order/place").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("locationLatitude", 1, "locationLongitude", 1, "items", items)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.details.items").exists());
    }

    @Test
    void adminEmailAlreadyInUseDoesNotBreakStartup() throws Exception {
        signup("kate", "kate@example.com");
        runAdminInitializer("second-admin", "second-admin-password", "kate@example.com");
        assertThat(userRepository.findOneByUsername("second-admin")).get()
                .satisfies(admin -> assertThat(admin.getEmail()).isNull());
    }

    @Test
    void exceptionHandlerMapsDatabaseAndUploadErrors() {
        CustomExceptionHandler handler = new CustomExceptionHandler();
        assertThat(handler.handleDataIntegrityViolation(new DataIntegrityViolationException("dup",
                new SQLException("duplicate key", "23505"))).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleDataIntegrityViolation(new DataIntegrityViolationException("null",
                new SQLException("null value", "23502"))).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(handler.handleMaxUploadSize(new MaxUploadSizeExceededException(5))
                .getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void abandonedUnverifiedAccountCanBeReclaimed() throws Exception {
        long squatterId = signup("frank", "frank@example.com");
        mvc.perform(post("/api/user/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupBody("frank2", "frank@example.com"))))
                .andExpect(status().isConflict());

        var otp = otpRepository.findOneByUserId(squatterId).orElseThrow();
        otpRepository.save(otp.setExpiryDate(new Date(System.currentTimeMillis() - 1000)));

        long ownerId = signup("frank2", "frank@example.com");
        assertThat(ownerId).isNotEqualTo(squatterId);
    }

    @Test
    void otpResendsAreCappedPerDay() throws Exception {
        long userId = signup("gina", "gina@example.com");
        var otp = otpRepository.findOneByUserId(userId).orElseThrow();
        Date past = new Date(System.currentTimeMillis() - 120_000);
        otpRepository.save(otp.setSentDate(past));

        mvc.perform(post("/api/user/resend_otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId))))
                .andExpect(status().isOk());

        otp = otpRepository.findOneByUserId(userId).orElseThrow();
        otpRepository.save(otp.setSentDate(past).setResendCount(5));
        String codeAtCap = otp.getOtp();
        mvc.perform(post("/api/user/resend_otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId))))
                .andExpect(status().isOk());
        // Over the daily cap nothing is sent or changed, but the answer is the same as for any other id.
        var after = otpRepository.findOneByUserId(userId).orElseThrow();
        assertThat(after.getResendCount()).isEqualTo(5);
        assertThat(after.getOtp()).isEqualTo(codeAtCap);
    }

    @Test
    void signupLimitsAllSignupsGenerouslyAndConflictsStrictly() {
        SignupRateLimiter limiter = new SignupRateLimiter(50, 3);
        // Ordinary sign-ups from a shared address are allowed up to the generous cap...
        for (int i = 0; i < 50; i++) {
            limiter.begin("10.0.0.5");
            limiter.finish("10.0.0.5", false);
        }
        assertThatThrownBy(() -> limiter.begin("10.0.0.5")).isInstanceOf(MarketException.class);

        // ...while hitting taken emails is limited strictly.
        for (int i = 0; i < 3; i++) {
            limiter.begin("10.0.0.6");
            limiter.finish("10.0.0.6", true);
        }
        assertThatThrownBy(() -> limiter.begin("10.0.0.6"))
                .isInstanceOf(MarketException.class)
                .satisfies(e -> assertThat(((MarketException) e).getRetryAfterSeconds()).isPositive());
        limiter.begin("10.0.0.7");
    }

    @Test
    void boundedMapDropsTheLeastRecentlyUsedEntry() {
        BoundedLruMap<Integer> map = new BoundedLruMap<>(3);
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);
        map.get("a");
        map.put("d", 4);
        assertThat(map.get("b")).isNull();
        assertThat(map.get("a")).isEqualTo(1);
        assertThat(map.size()).isEqualTo(3);
    }

    @Test
    void limitersKeepWorkingWhenTheirMapsAreFull() {
        SignupRateLimiter signups = new SignupRateLimiter(50, 3);
        LoginAttemptService attempts = new LoginAttemptService();
        for (int i = 0; i <= 100_000; i++) {
            String address = "10." + (i >> 16 & 255) + "." + (i >> 8 & 255) + "." + (i & 255);
            signups.begin(address);
            signups.finish(address, false);
            attempts.beginAttempt("someone", address);
        }
        // A newcomer can still sign up...
        signups.begin("203.0.113.50");
        // ...and a new (username, client) pair is still counted and blocked after 5 failures.
        for (int i = 0; i < 5; i++)
            failSignin(attempts, "target", "203.0.113.60");
        assertThatThrownBy(() -> attempts.beginAttempt("target", "203.0.113.60"))
                .isInstanceOf(MarketException.class);
    }

    @Test
    void signupsInProgressCountTowardTheConflictLimit() {
        // A parallel burst: none has finished (so no conflict is recorded yet), but the 4th is already refused.
        SignupRateLimiter limiter = new SignupRateLimiter(50, 3);
        for (int i = 0; i < 3; i++)
            limiter.begin("10.0.0.8");
        assertThatThrownBy(() -> limiter.begin("10.0.0.8")).isInstanceOf(MarketException.class);
        // Once they finish without conflict, there is room again.
        for (int i = 0; i < 3; i++)
            limiter.finish("10.0.0.8", false);
        limiter.begin("10.0.0.8");
    }

    @Test
    void parallelWrongPasswordsGetOnlyFiveTries() throws Exception {
        long userId = signup("tara", "tara@example.com");
        verifyUser(userId, "tara@example.com");
        int requests = 20;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++)
                statuses.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(post("/api/user/signin").contentType(MediaType.APPLICATION_JSON)
                                    .content(json(Map.of("username", "tara", "password", "wrong-password"))))
                            .andReturn().getResponse().getStatus();
                }));
            start.countDown();
            int checked = 0;
            for (Future<Integer> status : statuses)
                if (status.get() == 401)
                    checked++;
            // Each attempt is counted before the password check, so only 5 passwords are actually tried.
            assertThat(checked).isEqualTo(5);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void onlyExpectedSqlErrorsAreFilteredFromLogs() {
        ExpectedSqlErrorLogFilter filter = new ExpectedSqlErrorLogFilter();
        ch.qos.logback.classic.Logger sqlLogger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger("org.hibernate.engine.jdbc.spi.SqlExceptionHelper");
        ch.qos.logback.classic.Level error = ch.qos.logback.classic.Level.ERROR;
        assertThat(filter.decide(null, sqlLogger, error, "ERROR: canceling statement due to lock timeout", null, null))
                .isEqualTo(FilterReply.DENY);
        assertThat(filter.decide(null, sqlLogger, error, "SQL Error: 0, SQLState: 23505", null, null))
                .isEqualTo(FilterReply.DENY);
        assertThat(filter.decide(null, sqlLogger, error, "ERROR: column \"x\" does not exist", null, null))
                .isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void malformedJsonAndUnknownItemsUseTheStandardErrorShape() throws Exception {
        mvc.perform(post("/api/user/signin").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errors.message").exists());

        long userId = signup("hank", "hank@example.com");
        verifyUser(userId, "hank@example.com");
        String token = signin("hank", "password123");
        mvc.perform(post("/api/order/place").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("locationLatitude", 1, "locationLongitude", 1, "items", Map.of("999999", 1))))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors.message").value("Items not found: [999999]"));
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/item/all")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/order/place").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/item/all").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiDocsArePublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/order/place']").exists());
    }

    @Test
    void invalidRequestsReturnValidationErrors() throws Exception {
        mvc.perform(post("/api/user/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "x", "email", "not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("VALIDATION_EXCEPTION"))
                .andExpect(jsonPath("$.errors.details.email").exists())
                .andExpect(jsonPath("$.errors.details.password").exists());

        mvc.perform(post("/api/user/verify").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.details.userId").exists());
    }

    @Test
    void duplicateSignupIsRejected() throws Exception {
        signup("carol", "carol@example.com");
        mvc.perform(post("/api/user/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupBody("carol", "other@example.com"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("DUPLICATE_ENTITY"));
    }

    private long signup(String username, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/user/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupBody(username, email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.password").doesNotExist())
                .andReturn();
        return body(result).path("payload").path("id").asLong();
    }

    private String capturedOtp(String email) {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(messageService, atLeastOnce()).sendEmailMessage(eq(email), anyString(), text.capture());
        return text.getValue().replaceAll("\\D", "");
    }

    private void verifyUser(long userId, String email) throws Exception {
        String otp = capturedOtp(email);

        mvc.perform(post("/api/user/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId, "otp", otp))))
                .andExpect(status().isOk());
    }

    private static void failSignin(LoginAttemptService attempts, String username, String clientIp) {
        attempts.beginAttempt(username, clientIp);
        attempts.recordFailure(username, clientIp);
    }

    private void runAdminInitializer(String username, String password, String email) {
        AdminInitializer initializer = new AdminInitializer(userRepository, passwordEncoder, username, password, email);
        transactionTemplate.executeWithoutResult(status -> initializer.run(null));
    }

    private String signin(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/user/signin").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).path("payload").path("token").asText();
    }

    private static Map<String, Object> signupBody(String username, String email) {
        return Map.of("username", username, "email", email, "password", "password123",
                "firstName", "Test", "lastName", "User", "mobileNumber", "+96170000000");
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
