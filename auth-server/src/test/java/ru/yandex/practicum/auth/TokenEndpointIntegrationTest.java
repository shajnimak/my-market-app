package ru.yandex.practicum.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Выдача токенов по Client Credentials Flow: аутентификация клиента, скоупы, audience сервера ресурсов,
 * подпись ключом из JWK Set. Конфигурация одна на все тесты модуля, поэтому контекст берётся из кеша.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TokenEndpointIntegrationTest {

    private static final String CLIENT_ID = "market-app";
    private static final String CLIENT_SECRET = "market-app-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void token_shouldBeIssuedForPaymentServiceAudience_whenClientRequestsPaymentScopes() throws Exception {
        String response = requestToken(httpBasic(CLIENT_ID, CLIENT_SECRET), "payments.read payments.write")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);

        Jwt jwt = jwtDecoder.decode(body.get("access_token").asText());

        assertThat(jwt.getSubject()).isEqualTo(CLIENT_ID);
        assertThat(jwt.getIssuer()).hasToString("http://localhost:9000");
        assertThat(jwt.getAudience()).contains("payment-service", CLIENT_ID);
        assertThat(jwt.getClaimAsStringList("scope")).containsExactlyInAnyOrder("payments.read", "payments.write");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(5));
        assertThat(body.get("scope").asText().split(" ")).containsExactlyInAnyOrder("payments.read", "payments.write");
        assertThat(body.get("expires_in").asLong()).isBetween(290L, 300L);
    }

    @Test
    void token_shouldNotTargetPaymentService_whenNoPaymentScopeRequested() throws Exception {
        String response = mockMvc.perform(tokenRequest()
                        .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(objectMapper.readTree(response).get("access_token").asText());

        assertThat(jwt.getAudience()).containsExactly(CLIENT_ID);
    }

    @Test
    void token_shouldBeRefused_whenClientSecretWrong() throws Exception {
        requestToken(httpBasic(CLIENT_ID, "wrong-secret"), "payments.read")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void token_shouldBeRefused_whenClientUnknown() throws Exception {
        requestToken(httpBasic("unknown-app", CLIENT_SECRET), "payments.read")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void token_shouldBeRefused_whenClientNotAuthenticated() throws Exception {
        mockMvc.perform(tokenRequest()
                        .param("grant_type", "client_credentials")
                        .param("scope", "payments.read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void token_shouldBeRefused_whenScopeNotRegisteredForClient() throws Exception {
        requestToken(httpBasic(CLIENT_ID, CLIENT_SECRET), "payments.admin")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_scope"));
    }

    @Test
    void token_shouldBeRefused_forGrantTypeOtherThanClientCredentials() throws Exception {
        mockMvc.perform(tokenRequest()
                        .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .param("grant_type", "authorization_code")
                        .param("code", "any")
                        .param("redirect_uri", "http://localhost:8080"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void jwkSet_shouldBePublic_soResourceServersCanVerifySignatures() throws Exception {
        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
    }

    @Test
    void metadata_shouldAdvertiseTokenEndpointAndClientCredentialsGrant() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:9000"))
                .andExpect(jsonPath("$.token_endpoint").value("http://localhost:9000/oauth2/token"))
                .andExpect(jsonPath("$.jwks_uri").value("http://localhost:9000/oauth2/jwks"));
    }

    @Test
    void otherEndpoints_shouldBeClosed() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/login"))
                .andExpect(status().isForbidden());
    }

    private ResultActions requestToken(
            RequestPostProcessor clientAuthentication,
            String scope) throws Exception {
        return mockMvc.perform(tokenRequest()
                .with(clientAuthentication)
                .param("grant_type", "client_credentials")
                .param("scope", scope));
    }

    private static MockHttpServletRequestBuilder tokenRequest() {
        return post("/oauth2/token");
    }
}
