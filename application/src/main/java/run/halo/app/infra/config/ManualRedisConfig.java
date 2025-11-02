package run.halo.app.infra.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Provide an explicit LettuceConnectionFactory constructed from environment/RedisProperties.
 * This ensures Lettuce is configured with the intended host/port (not default localhost) and
 * avoids startup ordering/auto-config race which previously led to connections to localhost.
 */
@Configuration
public class ManualRedisConfig {

    private static final Logger logger = LoggerFactory.getLogger(ManualRedisConfig.class);

    @Autowired(required = false)
    private RedisProperties redisProperties;

    @Autowired
    private Environment environment;

    @Bean
    @Primary
    public RedisConnectionFactory lettuceConnectionFactory() {
        String envHost = environment.getProperty("spring.redis.host", environment.getProperty("SPRING_REDIS_HOST"));
        String host = envHost != null && !envHost.isBlank() ? envHost : (redisProperties != null ? redisProperties.getHost() : null);
        Integer propPort = (redisProperties != null) ? redisProperties.getPort() : null;
        String envPort = environment.getProperty("spring.redis.port", environment.getProperty("SPRING_REDIS_PORT"));
        int port = 6379;
        if (envPort != null && !envPort.isBlank()) {
            try {
                port = Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {
            }
        } else if (propPort != null) {
            port = propPort;
        }

        if (host == null || host.isBlank()) {
            host = "localhost"; // fallback, but we will log to help trace
        }

        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(host, port);
        if (redisProperties != null && redisProperties.getPassword() != null) {
            try {
                if (!redisProperties.getPassword().isEmpty()) {
                    cfg.setPassword(redisProperties.getPassword());
                }
            } catch (Exception ignored) {
            }
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(cfg);
        // initialize now to surface configuration issues early
        try {
            factory.afterPropertiesSet();
        } catch (Exception e) {
            logger.warn("Failed to initialize LettuceConnectionFactory during afterPropertiesSet: {}", e.getMessage(), e);
        }

        logger.info("ManualRedisConfig created LettuceConnectionFactory -> host='{}', port='{}', beanClass={}", host, port, factory.getClass().getName());
        return factory;
    }
}
