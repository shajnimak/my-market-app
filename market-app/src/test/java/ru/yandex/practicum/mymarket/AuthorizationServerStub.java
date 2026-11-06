package ru.yandex.practicum.mymarket;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Заглушка token endpoint сервера авторизации на {@link MockWebServer}: выдаёт access-токены по Client Credentials Flow
 * клиенту {@value #CLIENT_ID} с секретом {@value #CLIENT_SECRET} (HTTP Basic) и запоминает запросы токенов.
 * Выданные токены проверяет {@link PaymentServiceStub}. Клиента можно «разрегистрировать» —
 * тогда сервер отвечает {@code 401 invalid_client}, как настоящий сервер авторизации на неверный секрет.
 */
public final class AuthorizationServerStub {

    public static final String CLIENT_ID = "market-app";
    public static final String CLIENT_SECRET = "market-app-secret";

    private static final String EXPECTED_AUTHORIZATION = "Basic " + Base64.getEncoder()
            .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));

    private final MockWebServer server = new MockWebServer();
    private final Set<String> validTokens = ConcurrentHashMap.newKeySet();
    private final List<Map<String, String>> tokenRequests = new CopyOnWriteArrayList<>();
    private final AtomicInteger issuedTokens = new AtomicInteger();
    private volatile boolean clientRegistered = true;

    public AuthorizationServerStub() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return handle(request);
            }
        });
        try {
            server.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String tokenUri() {
        return server.url("/oauth2/token").toString();
    }

    /**
     * Возвращает заглушку в исходное состояние. Уже выданные токены остаются действительными.
     */
    public void reset() {
        clientRegistered = true;
        tokenRequests.clear();
    }

    public void setClientRegistered(boolean clientRegistered) {
        this.clientRegistered = clientRegistered;
    }

    /**
     * Делает все выданные токены недействительными — как после смены ключа подписи на сервере авторизации.
     */
    public void revokeTokens() {
        validTokens.clear();
    }

    public boolean isValid(String token) {
        return validTokens.contains(token);
    }

    /**
     * Параметры формы всех успешно аутентифицированных и отклонённых запросов токена.
     */
    public List<Map<String, String>> getTokenRequests() {
        return List.copyOf(tokenRequests);
    }

    private MockResponse handle(RecordedRequest request) {
        if (!"POST".equals(request.getMethod()) || !"/oauth2/token".equals(request.getPath())) {
            return new MockResponse().setResponseCode(404);
        }
        Map<String, String> form = parseForm(request.getBody().readUtf8());
        tokenRequests.add(form);
        if (!clientRegistered || !EXPECTED_AUTHORIZATION.equals(request.getHeader("Authorization"))) {
            return json(401, "{\"error\": \"invalid_client\"}");
        }
        if (!"client_credentials".equals(form.get("grant_type"))) {
            return json(400, "{\"error\": \"unsupported_grant_type\"}");
        }
        String token = "access-token-" + issuedTokens.incrementAndGet();
        validTokens.add(token);
        return json(200, "{\"access_token\": \"" + token + "\", \"token_type\": \"Bearer\", \"expires_in\": 300, "
                + "\"scope\": \"" + form.getOrDefault("scope", "") + "\"}");
    }

    private static Map<String, String> parseForm(String body) {
        return Arrays.stream(body.split("&"))
                .filter(pair -> !pair.isEmpty())
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : ""));
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
