package run.halo.app.infra.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * When Redis cache is enabled, perform optional initialization:
 * - create configured cache names by invoking CacheManager.getCache(name)
 * - optionally clear keys for these caches (useful for clean start in dev)
 */
@Configuration
// Activation is checked at runtime to avoid bean-registration ordering issues
@EnableConfigurationProperties(RedisInitProperties.class)
public class RedisStartupInitializer implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger logger = LoggerFactory.getLogger(RedisStartupInitializer.class);

    private final RedisInitProperties properties;
    private final java.util.Optional<StringRedisTemplate> stringRedisTemplate;
    private final Environment environment;
    private final org.springframework.cache.CacheManager cacheManager;

    public RedisStartupInitializer(RedisInitProperties properties,
                                   java.util.Optional<StringRedisTemplate> stringRedisTemplate,
                                   org.springframework.cache.CacheManager cacheManager,
                                   Environment environment) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheManager = cacheManager;
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (properties == null) {
            return;
        }

        String haloCache = environment.getProperty("halo.cache.type", "");
        String springCache = environment.getProperty("spring.cache.type", "");
        if (!"redis".equalsIgnoreCase(haloCache) && !"redis".equalsIgnoreCase(springCache)) {
            // Redis caching not enabled, skip initialization
            return;
        }

        if (!CollectionUtils.isEmpty(properties.getInitialNames())) {
            logger.info("Initializing Redis caches: {}", properties.getInitialNames());
            properties.getInitialNames().forEach(name -> {
                try {
                    // Ensure cache is created by accessing it
                    org.springframework.cache.Cache cache = cacheManager.getCache(name);
                    if (cache == null) {
                        logger.warn("CacheManager did not return a cache for name '{}', it may be created lazily later.", name);
                    }

                    if (properties.isClearOnStartup()) {
                        // Attempt to delete keys under common cache prefixes
                        // Common Spring RedisCache prefix uses <cacheName>::<key>
                        String pattern1 = name + "::*";
                        String pattern2 = name + ":*";

                        deleteKeysByPattern(pattern1);
                        deleteKeysByPattern(pattern2);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to initialize cache '{}' : {}", name, e.getMessage(), e);
                }
            });
        }

        // Try to write a simple initialization marker if Redis template is present.
        // Retry a few times to tolerate transient connection timing issues when Redis is just becoming available.
        if (stringRedisTemplate.isPresent()) {
            String markerKey = "halo:initialized";
            int attempts = 5;
            for (int i = 1; i <= attempts; i++) {
                try {
                    Boolean set = stringRedisTemplate.get().opsForValue().setIfAbsent(markerKey, String.valueOf(System.currentTimeMillis()));
                    if (Boolean.TRUE.equals(set)) {
                        logger.info("Wrote Redis initialization marker '{}' on attempt {}/{}", markerKey, i, attempts);
                    } else {
                        logger.debug("Redis initialization marker '{}' already exists (attempt {}/{})", markerKey, i, attempts);
                    }
                    break;
                } catch (Exception e) {
                    logger.warn("Attempt {}/{}: Failed to write Redis initialization marker: {}", i, attempts, e.getMessage());
                    if (i == attempts) {
                        logger.warn("Giving up writing Redis initialization marker after {} attempts", attempts, e);
                    } else {
                        try {
                            Thread.sleep(500L);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        } else {
            logger.debug("StringRedisTemplate not available, skipping Redis marker creation");
        }
    }

    private void deleteKeysByPattern(String pattern) {
        try {
            if (stringRedisTemplate.isPresent()) {
                Set<String> keys = stringRedisTemplate.get().keys(pattern);
                if (!CollectionUtils.isEmpty(keys)) {
                    logger.info("Deleting {} keys matching pattern '{}'", keys.size(), pattern);
                    stringRedisTemplate.get().delete(keys);
                }
            } else {
                logger.debug("StringRedisTemplate not present: cannot delete keys for pattern {}", pattern);
            }
        } catch (Exception e) {
            logger.warn("Error deleting keys by pattern '{}': {}", pattern, e.getMessage(), e);
        }
    }
}
