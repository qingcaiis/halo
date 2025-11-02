package run.halo.app.infra.config;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Optional Redis-based CacheManager. Enabled when property `halo.cache.type=redis` or
 * `spring.cache.type=redis` is set and a Redis connection factory is available on the classpath.
 *
 * This bean intentionally only loads when the property is set so existing behavior (Caffeine/default)
 * remains untouched unless the operator explicitly enables Redis.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnExpression("'${halo.cache.type:}' == 'redis' or '${spring.cache.type:}' == 'redis'")
public class RedisCacheManagerConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        org.springframework.data.redis.cache.RedisCacheConfiguration config = org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                new GenericJackson2JsonRedisSerializer()))
            // sensible default TTL; can be overridden per-cache via configuration
            .entryTtl(Duration.ofHours(1));

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(config)
            .build();
    }
}
