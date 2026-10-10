package com.zija.ai.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
class OpenRouterJevDecisionClient implements JevDecisionClient {

    private final JevSettings settings;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    OpenRouterJevDecisionClient(JevSettings settings, ObjectMapper objectMapper) {
        this.settings = settings;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public JevChoiceAnswer decideChoice(
            Object state,
            String questionId,
            String instructions,
            Map<String, String> criteria
    ) {
        if (!settings.configured()) {
            throw new JevDecisionException("JEV_NOT_CONFIGURED");
        }
        Map<String, Object> question = Map.of(
                "type", "choice",
                "instructions", instructions,
                "criteria", criteria);
        Map<String, Object> body = Map.of(
                "model", settings.model(),
                "state", state,
                "questions", Map.of(questionId, question));
        try {
            String payload = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(settings.baseUrl()))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new JevDecisionException("JEV_HTTP_" + response.statusCode());
            }
            return parseChoice(objectMapper.readTree(response.body()), questionId, criteria.keySet());
        } catch (JevDecisionException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new JevDecisionException("JEV_INTERRUPTED", exception);
        } catch (Exception exception) {
            throw new JevDecisionException("JEV_CALL_FAILED", exception);
        }
    }

    private static JevChoiceAnswer parseChoice(JsonNode root, String questionId, java.util.Set<String> allowed) {
        JsonNode answer = root.path("answers").path(questionId);
        if (answer.isMissingNode()) {
            throw new JevDecisionException("JEV_MISSING_ANSWER");
        }
        String choice = answer.path("choice").asText(null);
        if (choice == null || choice.isBlank() || !allowed.contains(choice)) {
            throw new JevDecisionException("JEV_INVALID_CHOICE");
        }
        double confidence = answer.path("confidence").asDouble(Double.NaN);
        Map<String, Double> probabilities = new LinkedHashMap<>();
        JsonNode probs = answer.path("probabilities");
        if (probs.isObject()) {
            probs.fields().forEachRemaining(entry ->
                    probabilities.put(entry.getKey(), entry.getValue().asDouble()));
        }
        if (Double.isNaN(confidence) && !probabilities.isEmpty()) {
            confidence = probabilities.getOrDefault(choice, 0.0);
        }
        if (Double.isNaN(confidence)) {
            confidence = 0.0;
        }
        return new JevChoiceAnswer(choice, confidence, Map.copyOf(probabilities));
    }

    static final class JevDecisionException extends RuntimeException {
        JevDecisionException(String code) {
            super(code);
        }

        JevDecisionException(String code, Throwable cause) {
            super(code, cause);
        }
    }
}
