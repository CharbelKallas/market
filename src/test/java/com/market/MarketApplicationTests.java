package com.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.market.config.AdminInitializer;
import com.market.exception.CustomExceptionHandler;
import com.market.repository.UserOtpRepository;
import com.market.repository.UserRepository;
import com.market.service.MessageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
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

        // A resend right after sign-up is refused by the cooldown.
        mvc.perform(post("/api/user/resend_otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId))))
                .andExpect(status().isBadRequest());
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
                .andExpect(status().isTooManyRequests());
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

        otp = otpRepository.findOneByUserId(userId).orElseThrow();
        assertThat(otp.getResendCount()).isZero();
        assertThat(otp.getSentDate()).isNull();
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
        mvc.perform(post("/api/user/resend_otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").value("Too many OTP requests. Please try again tomorrow."));
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
