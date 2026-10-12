package com.restaurant.reservation.infrastructure.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservation.application.port.NoShowFeatures;
import com.restaurant.reservation.application.port.NoShowPrediction;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

import static com.restaurant.reservation.domain.model.VenueType.RESTAURANT;
import static org.assertj.core.api.Assertions.assertThat;

class HttpNoShowPredictorTest {

    private static final NoShowFeatures FEATURES = new NoShowFeatures(5, 20, 36.5, 4, 12, 0.25, RESTAURANT);
    private static final Duration TIMEOUT = Duration.ofMillis(400);
    private static final Duration COOLDOWN = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> receivedBodies = new CopyOnWriteArrayList<>();
    private final List<String> receivedMethods = new CopyOnWriteArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();

    private HttpServer server;
    private volatile int status = 200;
    private volatile String responseBody = "{\"probability\": 0.37, \"modelVersion\": \"abc123def456\"}";
    private volatile long delayMillis = 0;
    private MutableClock clock;

    @BeforeEach
    void startServer() throws IOException {
        clock = new MutableClock(Instant.parse("2030-01-01T09:00:00Z"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict", exchange -> {
            calls.incrementAndGet();
            receivedMethods.add(exchange.getRequestMethod());
            receivedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private HttpNoShowPredictor predictor() {
        return predictorFor("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private HttpNoShowPredictor predictorFor(String baseUrl) {
        return new HttpNoShowPredictor(objectMapper, clock, baseUrl, TIMEOUT, COOLDOWN);
    }

    @Test
    void returnsProbabilityAndModelVersionFromTheService() {
        NoShowPrediction prediction = predictor().predict(FEATURES);

        assertThat(prediction.isAvailable()).isTrue();
        assertThat(prediction.probability()).isEqualTo(0.37);
        assertThat(prediction.modelVersion()).isEqualTo("abc123def456");
    }

    @Test
    void sendsAllFeaturesAsJsonViaPost() throws Exception {
        predictor().predict(FEATURES);

        assertThat(receivedMethods).containsExactly("POST");
        JsonNode body = objectMapper.readTree(receivedBodies.get(0));
        assertThat(body.get("dayOfWeek").asInt()).isEqualTo(5);
        assertThat(body.get("hour").asInt()).isEqualTo(20);
        assertThat(body.get("leadTimeHours").asDouble()).isEqualTo(36.5);
        assertThat(body.get("partySize").asInt()).isEqualTo(4);
        assertThat(body.get("userBookingsCount").asInt()).isEqualTo(12);
        assertThat(body.get("userNoShowRate").asDouble()).isEqualTo(0.25);
        assertThat(body.get("venueType").asText()).isEqualTo("RESTAURANT");
        assertThat(body.size()).isEqualTo(7);
    }

    @Test
    void trailingSlashInBaseUrlIsTolerated() {
        NoShowPrediction prediction = predictorFor("http://127.0.0.1:" + server.getAddress().getPort() + "/").predict(FEATURES);

        assertThat(prediction.isAvailable()).isTrue();
    }

    @Test
    void acceptsBoundaryProbabilities() {
        responseBody = "{\"probability\": 0.0, \"modelVersion\": \"v\"}";
        assertThat(predictor().predict(FEATURES).probability()).isEqualTo(0.0);

        responseBody = "{\"probability\": 1, \"modelVersion\": \"v\"}";
        assertThat(predictor().predict(FEATURES).probability()).isEqualTo(1.0);
    }

    @Test
    void worksWithoutAModelVersionInTheAnswer() {
        responseBody = "{\"probability\": 0.2}";

        NoShowPrediction prediction = predictor().predict(FEATURES);

        assertThat(prediction.probability()).isEqualTo(0.2);
        assertThat(prediction.modelVersion()).isNull();
    }

    @Test
    void fallsBackWhenTheServiceAnswersWithAnError() {
        status = 503;
        responseBody = "{\"error\": \"Service Unavailable\"}";

        assertThat(predictor().predict(FEATURES).isAvailable()).isFalse();
    }

    @Test
    void fallsBackWhenTheServiceRejectsTheRequest() {
        status = 400;

        assertThat(predictor().predict(FEATURES).isAvailable()).isFalse();
    }

    @Test
    void fallsBackWhenTheAnswerIsNotJson() {
        responseBody = "<html>oops</html>";

        assertThat(predictor().predict(FEATURES).isAvailable()).isFalse();
    }

    @Test
    void fallsBackWhenTheProbabilityIsMissingOrNotANumber() {
        responseBody = "{\"modelVersion\": \"v\"}";
        assertThat(predictor().predict(FEATURES).isAvailable()).isFalse();

        clock.advance(COOLDOWN.plusSeconds(1));
        responseBody = "{\"probability\": \"high\"}";
        assertThat(predictor().predict(FEATURES).isAvailable()).isFalse();
    }

    @Test
    void fallsBackWhenTheProbabilityIsOutsideZeroAndOne() {
        responseBody = "{\"probability\": 1.5}";
        assertThat(predictor().predict(FEATURES).isAvailable()).isFalse();

        responseBody = "{\"probability\": -0.1}";
        assertThat(predictor().predict(FEATURES).isAvailable()).isFalse();
    }

    @Test
    void fallsBackWhenTheServiceIsTooSlow() {
        delayMillis = 1500;
        long started = System.nanoTime();

        NoShowPrediction prediction = predictor().predict(FEATURES);

        assertThat(prediction.isAvailable()).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1200));
    }

    @Test
    void fallsBackWhenNothingListensOnTheAddress() {
        int freePort = server.getAddress().getPort();
        server.stop(0);

        assertThat(predictorFor("http://127.0.0.1:" + freePort).predict(FEATURES).isAvailable()).isFalse();
    }

    @Test
    void doesNotCallAFailingServiceAgainDuringTheCooldown() {
        status = 500;
        HttpNoShowPredictor predictor = predictor();

        predictor.predict(FEATURES);
        predictor.predict(FEATURES);
        predictor.predict(FEATURES);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void triesAgainAfterTheCooldownAndRecovers() {
        status = 500;
        HttpNoShowPredictor predictor = predictor();
        predictor.predict(FEATURES);
        status = 200;

        assertThat(predictor.predict(FEATURES).isAvailable()).isFalse();
        clock.advance(COOLDOWN.plusSeconds(1));
        NoShowPrediction recovered = predictor.predict(FEATURES);

        assertThat(recovered.isAvailable()).isTrue();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void staysWithinTheCooldownUntilItReallyEnds() {
        status = 500;
        HttpNoShowPredictor predictor = predictor();
        predictor.predict(FEATURES);

        clock.advance(COOLDOWN.minusSeconds(1));
        predictor.predict(FEATURES);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void aSuccessfulCallClearsThePause() {
        HttpNoShowPredictor predictor = predictor();

        predictor.predict(FEATURES);
        predictor.predict(FEATURES);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void failedCallAfterRecoveryStartsANewCooldown() {
        status = 500;
        HttpNoShowPredictor predictor = predictor();
        predictor.predict(FEATURES);
        clock.advance(COOLDOWN.plusSeconds(1));
        predictor.predict(FEATURES);
        predictor.predict(FEATURES);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void unavailablePredictionCarriesNoProbability() {
        NoShowPrediction unavailable = NoShowPrediction.unavailable();

        assertThat(unavailable.isAvailable()).isFalse();
        assertThat(unavailable.probability()).isNull();
        assertThat(unavailable.modelVersion()).isNull();
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
