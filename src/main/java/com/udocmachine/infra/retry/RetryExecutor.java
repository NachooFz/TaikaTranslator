package com.udocmachine.infra.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryExecutor {
    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    @FunctionalInterface
    public interface RetryableAction<T> {
        T run() throws Exception;
    }

    /**
     * Executes a given action with retries, exponential backoff, and randomized jitter.
     */
    public static <T> T executeWithRetry(RetryableAction<T> action, int maxRetries, long initialDelayMs) throws Exception {
        int attempt = 0;
        long delay = initialDelayMs;
        
        while (true) {
            try {
                return action.run();
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxRetries) {
                    log.error("Action failed after {} attempts. Exceeded max retries.", attempt, e);
                    throw e;
                }
                
                // Randomized jitter is ±10% of the delay
                long jitter = (long) ((Math.random() * 2 - 1) * (delay * 0.1));
                long sleepTime = Math.max(10, delay + jitter);
                
                log.warn("Temporary error encountered (Attempt {}/{}). Retrying in {}ms. Error: {}", 
                        attempt, maxRetries, sleepTime, e.getMessage());
                
                Thread.sleep(sleepTime);
                delay *= 2.0; // Exponential backoff
            }
        }
    }
}
