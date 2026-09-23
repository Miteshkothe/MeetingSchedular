package com.meetingbooking.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.cache.Cache;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class RedisCacheIntegrationTest {
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Test
    void redisCacheReadsWritesAndEvictsCachedValues() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        try {
            RedisCacheManager manager = new RedisConfig().cacheManager(connectionFactory);
            Cache cache = manager.getCache("availability");
            assertNotNull(cache);
            cache.put("room:interval", "free");
            assertEquals("free", cache.get("room:interval", String.class));
            cache.evict("room:interval");
            assertNull(cache.get("room:interval"));
        } finally {
            connectionFactory.destroy();
        }
    }
}
