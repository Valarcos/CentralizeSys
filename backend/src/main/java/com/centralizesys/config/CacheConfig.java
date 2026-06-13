package com.centralizesys.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Spring Cache configuration using Caffeine as the backing implementation.
 *
 * <p>A single named cache, {@value #ACTIVE_TOKENS_CACHE}, maps JWT ID (jti) strings
 * to user IDs ({@code Long}). TTL is set to match the JWT expiration so that the cache
 * naturally evicts a token at exactly the same moment the token itself becomes invalid.
 * This eliminates the need for explicit cache-side expiry management on logout — the token
 * will be gone from the DB, and from the cache, at the same time.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Name of the in-memory cache that maps jti → userId for active sessions. */
    public static final String ACTIVE_TOKENS_CACHE = "activeTokens";

    /** Safety cap: prevents unbounded memory growth in long-running deployments. */
    public static final long CACHE_MAX_SIZE = 10_000L;

    @Value("${app.jwtExpirationInMs}")
    private long jwtExpirationInMs;

    /**
     * Configures the Caffeine-backed {@link CacheManager}.
     *
     * <p>TTL is set via {@code expireAfterWrite} to match the JWT lifetime.
     * Once a token expires in the JWT sense, it is automatically evicted here too.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(ACTIVE_TOKENS_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(CACHE_MAX_SIZE)
                .expireAfterWrite(jwtExpirationInMs, TimeUnit.MILLISECONDS));
        return manager;
    }
}
