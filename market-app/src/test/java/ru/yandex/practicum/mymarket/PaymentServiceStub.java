package ru.yandex.practicum.mymarket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Заглушка сервиса платежей на {@link MockWebServer}: отвечает по контракту {@code openapi/payment-api.yaml}
 * (баланс и списание со счёта покупателя, 402 при нехватке средств) и запоминает проведённые платежи.
 * Как настоящий сервер ресурсов, принимает только запросы с действующим access-токеном,
 * выданным {@link AuthorizationServerStub}, иначе отвечает 401. Можно сделать «недоступной» — тогда на любой
 * запрос отвечает 503.
 */
public final class PaymentServiceStub {

    public static final long DEFAULT_BALANCE = 1_000_000;

    private static final Pattern BALANCE = Pattern.compile("/api/accounts/([^/]+)/balance");
    private static final Pattern PAYMENTS = Pattern.compile("/api/accounts/([^/]+)/payments");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MockWebServer server = new MockWebServer();
    private final AuthorizationServerStub authorizationServer;
    private final Map<String, Long> balances = new ConcurrentHashMap<>();
    private final List<Payment> payments = new CopyOnWriteArrayList<>();
    private final List<String> usedTokens = new CopyOnWriteArrayList<>();
    private final AtomicInteger unauthorizedRequests = new AtomicInteger();
    private volatile boolean available = true;

    public PaymentServiceStub(AuthorizationServerStub authorizationServer) {
        this.authorizationServer = authorizationServer;
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

    public String url() {
        return server.url("/").toString();
    }

    public void reset() {
        balances.clear();
        payments.clear();
        usedTokens.clear();
        unauthorizedRequests.set(0);
        available = true;
    }

    public void setBalance(String username, long amount) {
        balances.put(username, amount);
    }

    public long getBalance(String username) {
        return balances.getOrDefault(username, DEFAULT_BALANCE);
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    /**
     * Успешно проведённые платежи.
     */
    public List<Payment> getPayments() {
        return List.copyOf(payments);
    }

    /**
     * Access-токены принятых запросов в порядке поступления.
     */
    public List<String> getUsedTokens() {
        return List.copyOf(usedTokens);
    }

    /**
     * Сколько запросов отклонено из-за отсутствующего или недействительного токена.
     */
    public int getUnauthorizedRequests() {
        return unauthorizedRequests.get();
    }

    private MockResponse handle(RecordedRequest request) {
        if (!available) {
            return new MockResponse().setResponseCode(503);
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")
                || !authorizationServer.isValid(authorization.substring("Bearer ".length()))) {
            unauthorizedRequests.incrementAndGet();
            return new MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Bearer");
        }
        usedTokens.add(authorization.substring("Bearer ".length()));
        String path = request.getPath();
        Matcher balance = BALANCE.matcher(path);
        if ("GET".equals(request.getMethod()) && balance.matches()) {
            return json(200, "{\"amount\": " + getBalance(balance.group(1)) + "}");
        }
        Matcher payment = PAYMENTS.matcher(path);
        if ("POST".equals(request.getMethod()) && payment.matches()) {
            return pay(payment.group(1), readAmount(request));
        }
        return new MockResponse().setResponseCode(404);
    }

    private synchronized MockResponse pay(String username, long amount) {
        long current = getBalance(username);
        if (current < amount) {
            return json(402, "{\"code\": \"INSUFFICIENT_FUNDS\", \"message\": \"Недостаточно средств: баланс "
                    + current + " руб., требуется " + amount + " руб.\"}");
        }
        balances.put(username, current - amount);
        payments.add(new Payment(username, amount));
        return json(200, "{\"amount\": " + amount + ", \"balance\": " + (current - amount) + "}");
    }

    private long readAmount(RecordedRequest request) {
        try {
            JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
            return body.get("amount").asLong();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    public record Payment(String username, long amount) {
    }
}
