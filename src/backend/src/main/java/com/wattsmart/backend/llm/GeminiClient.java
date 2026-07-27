package com.wattsmart.backend.llm;

public interface GeminiClient {

    String generateRecommendation(String systemPrompt, String userPrompt);
}
