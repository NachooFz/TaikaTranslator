package com.taikatranslator.infra.translator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taikatranslator.core.translator.TextTranslator;
import com.taikatranslator.core.translator.TranslationException;
import com.taikatranslator.infra.retry.RetryExecutor;

public class DeepLTranslator implements TextTranslator {
    private static final Logger log = LoggerFactory.getLogger(DeepLTranslator.class);
    private final String apiKey;
    private final String endpoint; // E.g., "https://api-free.deepl.com" or "https://api.deepl.com"
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client;
    private final int maxRetries;
    private final long initialDelayMs;

    public DeepLTranslator(String apiKey, String endpoint, int maxRetries, long initialDelayMs) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<String> translate(List<String> textBlocks) throws TranslationException {
        if (textBlocks == null || textBlocks.isEmpty()) {
            return new ArrayList<>();
        }

        log.info("Sending batch translation of {} blocks to DeepL...", textBlocks.size());
        
        try {
            return RetryExecutor.executeWithRetry(() -> {
                // Construct JSON request body for DeepL API
                // Endpoint: /v2/translate
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("text", textBlocks);
                requestBody.put("target_lang", "ES");
                requestBody.put("source_lang", "EN");
                requestBody.put("tag_handling", "xml");
                
                String jsonPayload = mapper.writeValueAsString(requestBody);
                String requestUrl = endpoint + "/v2/translate";
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(requestUrl))
                        .header("Authorization", "DeepL-Auth-Key " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                log.debug("DeepL translation response code: {}", response.statusCode());
                
                if (response.statusCode() != 200) {
                    throw new RuntimeException("DeepL translation failed with status code " + 
                            response.statusCode() + ": " + response.body());
                }
                
                JsonNode root = mapper.readTree(response.body());
                JsonNode translations = root.get("translations");
                
                List<String> results = new ArrayList<>();
                if (translations != null) {
                    for (JsonNode tNode : translations) {
                        results.add(tNode.get("text").asText());
                    }
                }
                
                if (results.size() != textBlocks.size()) {
                    throw new RuntimeException("DeepL returned " + results.size() + 
                            " translated segments but " + textBlocks.size() + " were requested.");
                }
                
                return results;
            }, maxRetries, initialDelayMs);
            
        } catch (Exception e) {
            log.error("DeepL translation failed", e);
            throw new TranslationException("DeepL translation error: " + e.getMessage(), e);
        }
    }
}
