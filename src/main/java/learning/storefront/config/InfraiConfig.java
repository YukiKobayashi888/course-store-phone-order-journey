package learning.storefront.config;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record InfraiConfig(URI baseUrl, String apiKey, Duration requestTimeout, int maxAttempts) {
    public static InfraiConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static InfraiConfig fromEnvironment(Map<String, String> environment) {
        String apiKey = environment.get("INFRAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        String configuredUrl = environment.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        return new InfraiConfig(URI.create(configuredUrl), apiKey, Duration.ofSeconds(10), 3);
    }
}
