package com.restaurant.reservation.infrastructure.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservation.application.port.NoShowFeatures;
import com.restaurant.reservation.application.port.NoShowPrediction;
import com.restaurant.reservation.application.port.NoShowPredictor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.ml.enabled", havingValue = "true", matchIfMissing = true)
public class HttpNoShowPredictor implements NoShowPredictor {

    private static final String PREDICT_PATH = "/predict";

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final URI endpoint;
    private final Duration timeout;
    private final Duration cooldown;
    private final AtomicReference<Instant> pausedUntil = new AtomicReference<>(Instant.MIN);

    public HttpNoShowPredictor(ObjectMapper objectMapper, Clock clock,
                               @Value("${app.ml.base-url:http://localhost:5002}") String baseUrl,
                               @Value("${app.ml.timeout:PT1S}") Duration timeout,
                               @Value("${app.ml.cooldown:PT30S}") Duration cooldown) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + PREDICT_PATH);
        this.timeout = timeout;
        this.cooldown = cooldown;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public NoShowPrediction predict(NoShowFeatures features) {
        Instant now = clock.instant();
        if (now.isBefore(pausedUntil.get())) {
            return NoShowPrediction.unavailable();
        }
        try {
            NoShowPrediction prediction = request(features);
            pausedUntil.set(Instant.MIN);
            return prediction;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed("interrupted", e);
        } catch (IOException | RuntimeException e) {
            return failed(e.getClass().getSimpleName(), e);
        }
    }

    private NoShowPrediction request(NoShowFeatures features) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload(features))))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("ML service answered with status " + response.statusCode());
        }
        JsonNode body = objectMapper.readTree(response.body());
        JsonNode probability = body.get("probability");
        if (probability == null || !probability.isNumber()) {
            throw new IOException("ML service answer has no numeric probability");
        }
        JsonNode version = body.get("modelVersion");
        return NoShowPrediction.of(probability.asDouble(), version == null ? null : version.asText());
    }

    private NoShowPrediction failed(String reason, Exception error) {
        pausedUntil.set(clock.instant().plus(cooldown));
        log.warn("No-show prediction is unavailable ({}: {}), continuing without it for {}",
                reason, error.getMessage(), cooldown);
        return NoShowPrediction.unavailable();
    }

    private static Map<String, Object> payload(NoShowFeatures features) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dayOfWeek", features.dayOfWeek());
        body.put("hour", features.hour());
        body.put("leadTimeHours", features.leadTimeHours());
        body.put("partySize", features.partySize());
        body.put("userBookingsCount", features.userBookingsCount());
        body.put("userNoShowRate", features.userNoShowRate());
        body.put("venueType", features.venueType().name());
        return body;
    }
}
