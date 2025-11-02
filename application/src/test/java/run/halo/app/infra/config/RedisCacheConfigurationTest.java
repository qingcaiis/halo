package run.halo.app.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.cache.RedisCacheManager;

class RedisCacheConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    void when_property_set_and_redis_factory_present_then_redis_cache_manager_created() {
        contextRunner.withPropertyValues("halo.cache.type=redis")
            .withBean(RedisConnectionFactory.class, () -> org.mockito.Mockito.mock(RedisConnectionFactory.class))
            .withUserConfiguration(RedisCacheManagerConfig.class)
            .run(context -> {
                assertThat(context).hasSingleBean(RedisCacheManager.class);
            });
    }

    @Test
    void when_property_not_set_then_no_redis_cache_manager() {
        contextRunner.withPropertyValues()
            .withUserConfiguration(RedisCacheManagerConfig.class)
            .run(context -> {
                assertThat(context).doesNotHaveBean(RedisCacheManager.class);
            });
    }
}
