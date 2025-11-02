package run.halo.app.infra.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import java.net.InetAddress;
import java.util.Arrays;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
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
    private final java.util.Optional<RedisConnectionFactory> redisConnectionFactory;
    private final Environment environment;
    private final org.springframework.cache.CacheManager cacheManager;

    public RedisStartupInitializer(RedisInitProperties properties,
                                   java.util.Optional<StringRedisTemplate> stringRedisTemplate,
                                   java.util.Optional<RedisConnectionFactory> redisConnectionFactory,
                                   org.springframework.cache.CacheManager cacheManager,
                                   Environment environment) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
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
        // Retry several times with exponential backoff to tolerate transient connection timing issues when Redis is just becoming available.
        if (stringRedisTemplate.isPresent()) {
            String markerKey = "halo:initialized";
            // Log resolved connection info from environment to help debugging
                try {
                    String redisUrl = environment.getProperty("spring.redis.url");
                    String redisHost = environment.getProperty("spring.redis.host", environment.getProperty("SPRING_REDIS_HOST", ""));
                    String redisPort = environment.getProperty("spring.redis.port", environment.getProperty("SPRING_REDIS_PORT", "6379"));
                    logger.info("Redis init will attempt to connect to: spring.redis.url='{}', host='{}', port='{}'", redisUrl, redisHost, redisPort);

                    // Diagnostic: resolve host from inside container and log addresses to help explain why Lettuce may try localhost
                    try {
                        if (redisHost != null && !redisHost.isBlank()) {
                            InetAddress[] addrs = InetAddress.getAllByName(redisHost);
                            String[] ips = Arrays.stream(addrs).map(InetAddress::getHostAddress).toArray(String[]::new);
                            logger.info("Resolved redis host '{}' to addresses: {}", redisHost, Arrays.toString(ips));
                        } else {
                            logger.info("redisHost is blank when attempting to resolve (spring.redis.host / SPRING_REDIS_HOST)");
                        }
                        // Also log system properties and JVM_OPTS env for visibility
                        logger.info("System properties: spring.redis.host='{}' spring.redis.port='{}' JVM_OPTS='{}'",
                            System.getProperty("spring.redis.host"), System.getProperty("spring.redis.port"), System.getenv("JVM_OPTS"));
                    } catch (Exception dnsEx) {
                        logger.warn("Failed to resolve redis host '{}' : {}", environment.getProperty("spring.redis.host"), dnsEx.getMessage());
                    }
                    // Additional diagnostics: inspect RedisConnectionFactory implementation and Lettuce configuration if available
                    try {
                        if (redisConnectionFactory.isPresent()) {
                            Object factory = redisConnectionFactory.get();
                            logger.info("RedisConnectionFactory implementation: {}", factory.getClass().getName());
                            if (factory instanceof LettuceConnectionFactory) {
                                LettuceConnectionFactory lcf = (LettuceConnectionFactory) factory;
                                var standalone = lcf.getStandaloneConfiguration();
                                logger.info("Lettuce StandaloneConfiguration -> host='{}', port='{}'", standalone.getHostName(), standalone.getPort());
                            }
                        } else if (stringRedisTemplate.isPresent()) {
                            Object cf = stringRedisTemplate.get().getConnectionFactory();
                            if (cf != null) {
                                logger.info("StringRedisTemplate ConnectionFactory impl: {}", cf.getClass().getName());
                            }
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to inspect RedisConnectionFactory: {}", ex.getMessage(), ex);
                    }
                } catch (Exception ignored) {
                }

            // Run the initialization asynchronously to avoid blocking the main startup thread
            Runnable task = () -> {
                int attempts = 10;
                for (int i = 1; i <= attempts; i++) {
                    try {
                        // If RedisConnectionFactory is available, try a lightweight ping first to get a clearer error
                        if (redisConnectionFactory.isPresent()) {
                            try {
                                String pong = redisConnectionFactory.get().getConnection().ping();
                                logger.debug("Redis ping response: {}", pong == null ? "null" : pong);
                            } catch (Exception pingEx) {
                                logger.debug("Redis ping failed on attempt {}/{}", i, attempts, pingEx);
                                throw pingEx;
                            }
                        }

                        Boolean set = stringRedisTemplate.get().opsForValue().setIfAbsent(markerKey, String.valueOf(System.currentTimeMillis()));
                        if (Boolean.TRUE.equals(set)) {
                            logger.info("Wrote Redis initialization marker '{}' on attempt {}/{}", markerKey, i, attempts);
                        } else {
                            logger.debug("Redis initialization marker '{}' already exists (attempt {}/{})", markerKey, i, attempts);
                        }
                        break;
                    } catch (Exception e) {
                        long backoff = 500L * (long) Math.pow(2, Math.min(i - 1, 6)); // exponential backoff up to ~32s
                        // Log full exception to see connection stacktrace and root cause
                        logger.warn(String.format("Attempt %d/%d: Failed to write Redis initialization marker", i, attempts), e);
                        if (i == attempts) {
                            logger.warn("Giving up writing Redis initialization marker after {} attempts", attempts, e);
                        } else {
                            try {
                                Thread.sleep(backoff);
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                }
            };

            Thread t = new Thread(task, "halo-redis-init");
            t.setDaemon(true);
            t.start();
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
