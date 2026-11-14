package com.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.market.service.MessageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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

    @MockBean
    private MessageService messageService;

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
        MockMultipartFile image = new MockMultipartFile("image", "apple.png", "image/png", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/item/new").file(image).param("itemName", "Apple").param("amount", "1.50")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        String adminToken = signin("admin", "admin-password");
        JsonNode item = body(mvc.perform(multipart("/api/item/new").file(image)
                        .param("itemName", "Apple").param("amount", "1.50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn()).path("payload");
        long itemId = item.path("id").asLong();
        assertThat(itemId).isPositive();

        mvc.perform(get("/api/item/all").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content[0].id").value(itemId))
                .andExpect(jsonPath("$.payload.content[0].amount").value(1.5))
                .andExpect(jsonPath("$.payload.content[0].hasImage").value(true));

        mvc.perform(get("/api/item/image").param("itemId", String.valueOf(itemId))
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));

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
    void changePassword_appliesToSignedInUser() throws Exception {
        long userId = signup("bob", "bob@example.com");
        verifyUser(userId, "bob@example.com");
        String token = signin("bob", "password123");

        mvc.perform(post("/api/user/change_password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("oldPassword", "wrong-password", "newPassword", "newpassword123")))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/user/change_password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("oldPassword", "password123", "newPassword", "newpassword123")))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        signin("bob", "newpassword123");
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

    private void verifyUser(long userId, String email) throws Exception {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(messageService, atLeastOnce()).sendEmailMessage(eq(email), anyString(), text.capture());
        String otp = text.getValue().replaceAll("\\D", "");

        mvc.perform(post("/api/user/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId, "otp", otp))))
                .andExpect(status().isOk());
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
