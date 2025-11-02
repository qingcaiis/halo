package run.halo.app.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "halo.cache")
public class RedisInitProperties {
    /**
     * Initial cache names to create on startup when Redis cache is enabled.
     */
    private List<String> initialNames = new ArrayList<>();

    /**
     * If true, on startup the initializer will attempt to remove keys for the configured caches
     * in Redis (use with caution in production).
     */
    private boolean clearOnStartup = false;

    public List<String> getInitialNames() {
        return initialNames;
    }

    public void setInitialNames(List<String> initialNames) {
        this.initialNames = initialNames;
    }

    public boolean isClearOnStartup() {
        return clearOnStartup;
    }

    public void setClearOnStartup(boolean clearOnStartup) {
        this.clearOnStartup = clearOnStartup;
    }
}
