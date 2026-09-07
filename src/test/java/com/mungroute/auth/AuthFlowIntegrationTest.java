package com.mungroute.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
@TestPropertySource(properties = "security.password-reset.expose-code=true")
class AuthFlowIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void signupLoginRefreshProtectedSessionLogoutAndPasswordResetWorkTogether() throws Exception {
        String suffix = "%08d".formatted(Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000));
        String email = "auth-" + suffix + "@example.com";
        String nickname = "auth-" + suffix;
        String phone = "010" + suffix;

        mockMvc.perform(get("/api/auth/check-email").param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        var signup = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "mungroute1",
                                  "nickname": "%s",
                                  "phoneNumber": "%s",
                                  "termsAgreed": true
                                }
                                """.formatted(email, nickname, phone)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn();

        JsonNode signupBody = objectMapper.readTree(signup.getResponse().getContentAsString());
        String accessToken = signupBody.get("accessToken").asText();
        Cookie refreshCookie = signup.getResponse().getCookie("MUNGROUTE_REFRESH");
        assertThat(refreshCookie).isNotNull();

        mockMvc.perform(get("/api/auth/session")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(nickname));

        var refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie rotatedRefreshCookie = refreshed.getResponse().getCookie("MUNGROUTE_REFRESH");
        assertThat(rotatedRefreshCookie).isNotNull();

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());

        var resetRequest = mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.demoVerificationCode").isNotEmpty())
                .andReturn();
        String verificationCode = objectMapper.readTree(resetRequest.getResponse().getContentAsString())
                .get("demoVerificationCode").asText();

        var verified = mockMvc.perform(post("/api/auth/password-reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","verificationCode":"%s"}
                                """.formatted(email, verificationCode)))
                .andExpect(status().isOk())
                .andReturn();
        String resetToken = objectMapper.readTree(verified.getResponse().getContentAsString())
                .get("resetToken").asText();

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resetToken":"%s","newPassword":"changed123"}
                                """.formatted(resetToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(rotatedRefreshCookie))
                .andExpect(status().isUnauthorized());

        var login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"changed123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie loginRefreshCookie = login.getResponse().getCookie("MUNGROUTE_REFRESH");
        assertThat(loginRefreshCookie).isNotNull();

        mockMvc.perform(post("/api/auth/logout").cookie(loginRefreshCookie))
                .andExpect(status().isNoContent());
    }
}
