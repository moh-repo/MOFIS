package com.dilaw.mohe.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    public static final String CODE_LISTS = "code-lists";
    public static final String SYS_PARAMETERS = "sys-parameters";
    public static final String LOCKOUT_POLICY = "lockout-policy";
    public static final String LOOKUPS = "lookups";
    public static final String COUNTRY_NAMES = "country-names";
    /**
     * R-19: per-user authorities for the JWT filter ({@code UserAuthorityCache}).
     * Evicted explicitly on every change that feeds it; the TTL is a backstop.
     * {@code ums.authority-cache.ttl-seconds=0} disables it (every request reads
     * the database, as before), which is the setting for a multi-instance
     * deployment without a shared eviction signal (decision D-10).
     */
    public static final String USER_AUTHORITIES = "user-authorities";

    /** R-18: the set of FIELD permission codes the administrators have defined (one entry). */
    public static final String FIELD_PERMISSIONS = "field-permissions";

    @Value("${ums.authority-cache.ttl-seconds:60}")
    private long authorityTtlSeconds;

    @Bean
    public CacheManagerCustomizer<CaffeineCacheManager> codeListCaches() {
        return manager -> {
            manager.registerCustomCache(CODE_LISTS,
                    Caffeine.newBuilder().maximumSize(200).expireAfterWrite(Duration.ofSeconds(60)).build());
            manager.registerCustomCache(SYS_PARAMETERS,
                    Caffeine.newBuilder().maximumSize(50).expireAfterWrite(Duration.ofSeconds(60)).build());
            manager.registerCustomCache(LOCKOUT_POLICY,
                    Caffeine.newBuilder().maximumSize(10).expireAfterWrite(Duration.ofSeconds(60)).build());
            manager.registerCustomCache(LOOKUPS,
                    Caffeine.newBuilder().maximumSize(200).expireAfterWrite(Duration.ofSeconds(60)).build());
            manager.registerCustomCache(COUNTRY_NAMES,
                    Caffeine.newBuilder().maximumSize(4).expireAfterWrite(Duration.ofMinutes(10)).build());
            manager.registerCustomCache(FIELD_PERMISSIONS,
                    Caffeine.newBuilder().maximumSize(1).expireAfterWrite(Duration.ofSeconds(60)).build());
            manager.registerCustomCache(USER_AUTHORITIES,
                    Caffeine.newBuilder().maximumSize(2000)
                            .expireAfterWrite(Duration.ofSeconds(Math.max(0, authorityTtlSeconds))).build());
        };
    }
}
