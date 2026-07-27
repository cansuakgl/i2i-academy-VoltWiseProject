package com.wattsmart.backend.llm;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.llm.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class GeminiRestClient implements GeminiClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiRestClient(
            @Value("${app.llm.gemini.base-url}") String baseUrl,
            @Value("${app.llm.gemini.api-key}") String apiKey,
            @Value("${app.llm.gemini.model}") String model,
            @Value("${app.llm.gemini.timeout}") Duration timeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String generateRecommendation(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured.");
        }

        GeminiResponse response;
        try {
            response = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .body(new GeminiRequest(
                            new SystemInstruction(List.of(new Part(systemPrompt))),
                            List.of(new Content("user", List.of(new Part(userPrompt)))),
                            new GenerationConfig(0.4, 700)))
                    .retrieve()
                    .body(GeminiResponse.class);
        } catch (RuntimeException exception) {
            log.warn("Gemini generateContent request failed. model={}, message={}", model, exception.getMessage(), exception);
            throw exception;
        }

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new IllegalStateException("Gemini returned an empty response.");
        }
        Content content = response.candidates().getFirst().content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            throw new IllegalStateException("Gemini returned a response without text.");
        }
        return content.parts().stream()
                .map(Part::text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Gemini returned blank text."));
    }

    private record GeminiRequest(
            SystemInstruction systemInstruction,
            List<Content> contents,
            GenerationConfig generationConfig
    ) {
    }

    private record SystemInstruction(List<Part> parts) {
    }

    private record Content(String role, List<Part> parts) {
    }

    private record Part(String text) {
    }

    private record GenerationConfig(double temperature, int maxOutputTokens) {
    }

    private record GeminiResponse(List<Candidate> candidates) {
    }

    private record Candidate(Content content) {
    }
}
