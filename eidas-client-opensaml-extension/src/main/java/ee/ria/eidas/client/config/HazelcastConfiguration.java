package ee.ria.eidas.client.config;

import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import ee.ria.eidas.client.session.HazelcastRequestSessionServiceImpl;
import ee.ria.eidas.client.session.RequestSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static ee.ria.eidas.client.session.HazelcastRequestSessionServiceImpl.UNANSWERED_REQUESTS_MAP;

@ConditionalOnProperty("eidas.client.hazelcast-enabled")
@EnableConfigurationProperties({
        EidasClientProperties.class
})
@Configuration
@Slf4j
public class HazelcastConfiguration {

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private EidasClientProperties eidasClientProperties;

    @Bean
    public HazelcastInstance hazelcast() {
        return Hazelcast.newHazelcastInstance(getConfig());
    }

    @Bean(name = "requestSessionService")
    public RequestSessionService hazelcastRequestSessionService() {
        return new HazelcastRequestSessionServiceImpl(eidasClientProperties, hazelcast());
    }

    private Config getConfig() {
        if (eidasClientProperties.getHazelcastConfig() != null) {
            Resource resource = resourceLoader.getResource(eidasClientProperties.getHazelcastConfig());
            try {
                final Config config;
                final URL configUrl = resource.getURL();
                log.info("Loading Hazelcast configuration from [{}]", configUrl);
                config = new XmlConfigBuilder(resource.getInputStream()).build();
                config.setConfigurationUrl(configUrl);
                config.setMapConfigs(buildHazelcastMapConfigurations());
                return config;
            } catch (final Exception e) {
                throw new IllegalStateException("Failed to initialize Hazelcasti instance: " + e.getMessage(), e);
            }
        } else {
            throw new IllegalStateException("Could not find the hazelcast configuration!");
        }
    }


    private Map<String, MapConfig> buildHazelcastMapConfigurations() {
        Map<String, MapConfig> mapConfigs = new HashMap<>();
        MapConfig mapConfig = this.createMapConfig();
        log.debug("Created Hazelcast map configuration");
        mapConfigs.put(UNANSWERED_REQUESTS_MAP, mapConfig);
        return mapConfigs;
    }

    private MapConfig createMapConfig() {
        log.debug("Creating Hazelcast map configuration");

        return (new MapConfig()).setName(UNANSWERED_REQUESTS_MAP)
                .setMaxIdleSeconds(eidasClientProperties.getHazelcastStorageTimeout())
                .setBackupCount(1)
                .setAsyncBackupCount(0)
                .setEvictionPolicy(EvictionPolicy.valueOf(eidasClientProperties.getHazelcastEvictionPolicy()))
                .setMaxSizeConfig(
                        (new MaxSizeConfig())
                                .setMaxSizePolicy(MaxSizeConfig.MaxSizePolicy.valueOf("USED_HEAP_PERCENTAGE"))
                                .setSize(eidasClientProperties.getHazelcastMaxHeapSizePercentage())
                );
    }
}
