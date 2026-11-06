package ru.yandex.practicum.payment;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Заменяет сервер авторизации в тестах: публикует JWK Set тестового ключа на {@link MockWebServer}
 * и подписывает этим ключом access-токены так же, как настоящий сервер авторизации
 * ({@code iss}, {@code sub} = client_id, {@code aud} = payment-service, {@code scope}).
 * Сервис платежей проверяет такие токены своим обычным {@code ReactiveJwtDecoder} из конфигурации.
 */
public final class TestAuthorizationServer {

    public static final String ISSUER = "http://localhost:9000";
    public static final String CLIENT_ID = "market-app";
    public static final String AUDIENCE = "payment-service";

    private final MockWebServer server = new MockWebServer();
    private final RSAKey signingKey = generateKey();
    private final NimbusJwtEncoder encoder = encoder(signingKey);

    public TestAuthorizationServer() {
        String jwkSet = new JWKSet(signingKey.toPublicJWK()).toString();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("/oauth2/jwks".equals(request.getPath())) {
                    return new MockResponse().setHeader("Content-Type", "application/json").setBody(jwkSet);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        try {
            server.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String jwkSetUri() {
        return server.url("/oauth2/jwks").toString();
    }

    /**
     * Токен, который сервер авторизации выдаёт витрине со скоупами {@code scopes}.
     */
    public String token(String... scopes) {
        return token(claims -> claims.claim("scope", List.of(scopes)));
    }

    /**
     * Токен витрины, в котором можно поменять любые claims (издателя, получателя, срок действия).
     */
    public String token(Consumer<JwtClaimsSet.Builder> customizer) {
        return sign(encoder, customizer);
    }

    /**
     * Токен с правильными claims, но подписанный ключом, которого нет в JWK Set сервера авторизации.
     */
    public String tokenSignedByForeignKey(String... scopes) {
        return sign(encoder(generateKey()), claims -> claims.claim("scope", List.of(scopes)));
    }

    private static String sign(NimbusJwtEncoder encoder, Consumer<JwtClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(CLIENT_ID)
                .audience(List.of(CLIENT_ID, AUDIENCE))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(Duration.ofMinutes(5)))
                .claim("scope", List.of());
        customizer.accept(claims);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static NimbusJwtEncoder encoder(RSAKey key) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
