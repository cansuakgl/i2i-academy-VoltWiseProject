package com.wattsmart.backend.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.llm.enabled", havingValue = "false")
public class UnavailableGeminiClient implements GeminiClient {

    @Override
    public String generateRecommendation(String systemPrompt, String userPrompt) {
        throw new IllegalStateException("Gemini client is not configured.");
    }
}
