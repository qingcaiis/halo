package run.halo.app.infra.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Backwards-compatible configuration class kept for historical reasons. It imports
 * the actual Redis cache configuration which is implemented in
 * {@link RedisCacheManagerConfig} to avoid name collision with Spring's
 * RedisCacheConfiguration class.
 */
@Configuration(proxyBeanMethods = false)
@Import(RedisCacheManagerConfig.class)
@Deprecated
public class RedisCacheConfiguration {

}
