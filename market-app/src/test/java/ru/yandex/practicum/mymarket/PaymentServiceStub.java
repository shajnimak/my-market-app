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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Заглушка сервиса платежей на {@link MockWebServer}: отвечает по контракту {@code openapi/payment-api.yaml}
 * (баланс, списание, 402 при нехватке средств) и запоминает проведённые платежи.
 * Можно сделать «недоступной» — тогда на любой запрос отвечает 503.
 */
public final class PaymentServiceStub {

    public static final long DEFAULT_BALANCE = 1_000_000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MockWebServer server = new MockWebServer();
    private final AtomicLong balance = new AtomicLong(DEFAULT_BALANCE);
    private final List<Long> payments = new CopyOnWriteArrayList<>();
    private volatile boolean available = true;

    public PaymentServiceStub() {
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
        balance.set(DEFAULT_BALANCE);
        payments.clear();
        available = true;
    }

    public void setBalance(long amount) {
        balance.set(amount);
    }

    public long getBalance() {
        return balance.get();
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    /**
     * Суммы успешно проведённых платежей.
     */
    public List<Long> getPayments() {
        return List.copyOf(payments);
    }

    private MockResponse handle(RecordedRequest request) {
        if (!available) {
            return new MockResponse().setResponseCode(503);
        }
        String path = request.getPath();
        if ("GET".equals(request.getMethod()) && "/api/balance".equals(path)) {
            return json(200, "{\"amount\": " + balance.get() + "}");
        }
        if ("POST".equals(request.getMethod()) && "/api/payments".equals(path)) {
            return pay(readAmount(request));
        }
        return new MockResponse().setResponseCode(404);
    }

    private synchronized MockResponse pay(long amount) {
        long current = balance.get();
        if (current < amount) {
            return json(402, "{\"code\": \"INSUFFICIENT_FUNDS\", \"message\": \"Недостаточно средств: баланс "
                    + current + " руб., требуется " + amount + " руб.\"}");
        }
        balance.set(current - amount);
        payments.add(amount);
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
}
