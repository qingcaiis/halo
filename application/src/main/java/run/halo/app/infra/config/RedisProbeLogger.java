package run.halo.app.infra.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

@Configuration
public class RedisProbeLogger implements EnvironmentAware {
    private static final Logger logger = LoggerFactory.getLogger(RedisProbeLogger.class);

    private Environment environment;

    private final Optional<StringRedisTemplate> stringRedisTemplate;

    public RedisProbeLogger(Optional<StringRedisTemplate> stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void probe() {
        String haloCache = environment.getProperty("halo.cache.type", "(not set)");
        String springCache = environment.getProperty("spring.cache.type", "(not set)");
        logger.info("RedisProbe: halo.cache.type='{}', spring.cache.type='{}', StringRedisTemplate present={}", haloCache, springCache, stringRedisTemplate.isPresent());
    }
}
