package learning.storefront.infrai;

import learning.storefront.config.InfraiConfig;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InfraiIdentityAndSmsClient implements IdentityAndSmsGateway {
    private final InfraiConfig config;
    private final HttpClient http;
    private final Sleeper sleeper;

    public InfraiIdentityAndSmsClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build(), Thread::sleep);
    }

    InfraiIdentityAndSmsClient(InfraiConfig config, HttpClient http, Sleeper sleeper) {
        this.config = config;
        this.http = http;
        this.sleeper = sleeper;
    }

    // Canonical capability marker: infrai.auth.phone.send_code
    @Override
    public void sendSignupCode(String phone, String requestId) {
        post("/v1/auth/phone/send_code", Map.of("phone", phone, "purpose", "signup", "locale", "en"), requestId);
    }

    @Override
    public VerifiedIdentity verifyPhone(String phone, String code, boolean login, String requestId) {
        Map<String, Object> data = post("/v1/auth/phone/verify",
                Map.of("phone", phone, "code", code, "login", login), requestId);
        return new VerifiedIdentity(requiredString(data, "user_id"), data);
    }

    @Override
    public Session createSession(String userId, String requestId) {
        Map<String, Object> data = post("/v1/auth/session/create",
                Map.of("user_id", userId, "method", "phone", "require_mfa", false), requestId);
        return new Session(requiredString(data, "session_id"), data);
    }

    // The SMS sender shares this client's API key and base URL with identity.
    @Override
    public void sendOrderUpdateCode(String phone, String requestId) {
        post("/v1/sms/otp", Map.of("to", phone), requestId);
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String requestId) {
        String json = Json.write(body);
        for (int attempt = 0; attempt < config.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", requestId)
                    .method("POST", HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException exception) {
                throw new IllegalStateException("Could not reach Infrai", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while calling Infrai", exception);
            }

            Map<String, Object> envelope = Json.readObject(response.body());
            if (response.statusCode() == 429 && attempt + 1 < config.maxAttempts()) {
                sleep(retryDelay(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = object(envelope.get("error"));
                throw new InfraiException(String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED")),
                        response.statusCode(), error);
            }
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("Infrai transport status " + response.statusCode());
            }
            return object(envelope.get("data"));
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private Duration retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try {
                return java.util.Optional.of(Duration.ofSeconds(Long.parseLong(value.trim())));
            } catch (NumberFormatException ignored) {
                return java.util.Optional.empty();
            }
        }).orElse(Duration.ofMillis(250L * (1L << attempt)));
    }

    private void sleep(Duration duration) {
        try {
            sleeper.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during rate-limit backoff", exception);
        }
    }

    private static String requiredString(Map<String, Object> data, String field) {
        Object value = data.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Infrai response data did not contain " + field);
        }
        return text;
    }

    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> copy = new LinkedHashMap<>();
        raw.forEach((key, item) -> copy.put(String.valueOf(key), item));
        return copy;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long milliseconds) throws InterruptedException;
    }
}
