package com.udocmachine.infra.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.udocmachine.infra.retry.RetryExecutor;

public class HttpClientWrapper {
    private static final Logger log = LoggerFactory.getLogger(HttpClientWrapper.class);
    private final HttpClient client;
    private final int maxRetries;
    private final long initialDelayMs;

    public HttpClientWrapper(int maxRetries, long initialDelayMs) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
    }

    public String get(String url, Map<String, String> headers) throws Exception {
        return RetryExecutor.executeWithRetry(() -> {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET();
            
            if (headers != null) {
                headers.forEach(builder::header);
            }
            
            HttpRequest request = builder.build();
            log.debug("HTTP GET Request: {}", url);
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("HTTP GET Response Status: {}", response.statusCode());
            
            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP GET failed with code " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        }, maxRetries, initialDelayMs);
    }

    public String post(String url, String jsonPayload, Map<String, String> headers) throws Exception {
        return RetryExecutor.executeWithRetry(() -> {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));
            
            if (headers != null) {
                headers.forEach(builder::header);
            }
            
            HttpRequest request = builder.build();
            log.debug("HTTP POST Request: {}", url);
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("HTTP POST Response Status: {}", response.statusCode());
            
            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP POST failed with code " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        }, maxRetries, initialDelayMs);
    }
}
