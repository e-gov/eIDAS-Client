package ee.ria.eidas.client.webapp.status;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.monitor.LocalMapStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.AbstractEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@ConditionalOnProperty("eidas.client.hazelcastConfig")
@ConfigurationProperties(
        prefix = "endpoints.hazelcast"
)
@Slf4j
public class HazelcastEndpoint extends AbstractEndpoint<Map<String, Object>> {

    @Autowired
    private HazelcastInstance hazelcastInstance;

    public HazelcastEndpoint() {
        super("hazelcast", false);
    }

    @Override
    public Map<String, Object> invoke() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clusterState", hazelcastInstance.getCluster().getClusterState().name());
        response.put("clusterSize", hazelcastInstance.getCluster().getMembers().size());

        try {
            List<Map<String, Object>> statsList = new ArrayList<>();
            hazelcastInstance.getConfig().getMapConfigs().keySet().forEach(key -> {
                final IMap map = hazelcastInstance.getMap(key);
                log.debug("Starting to collect hazelcast statistics for map [{}] identified by key [{}]...", map, key);
                Map<String, Object> mapStats = new LinkedHashMap<>();

                final LocalMapStats localMapStats = map.getLocalMapStats();

                mapStats.put("mapName", key);
                mapStats.put("maxCapacity", map.getLocalMapStats().total());
                mapStats.put("creationTime", localMapStats.getCreationTime());
                mapStats.put("ownedEntryCount", localMapStats.getOwnedEntryCount());
                mapStats.put("backupEntryCount", localMapStats.getBackupEntryCount());
                mapStats.put("backupCount", localMapStats.getBackupCount());
                mapStats.put("hitsCount", localMapStats.getHits());
                mapStats.put("lastUpdateTime", localMapStats.getLastUpdateTime());
                mapStats.put("lastAccessTime", localMapStats.getLastAccessTime());
                mapStats.put("lockedEntryCount", localMapStats.getLockedEntryCount());
                mapStats.put("dirtyEntryCount", localMapStats.getDirtyEntryCount());
                mapStats.put("totalGetLatency", localMapStats.getMaxGetLatency());
                mapStats.put("totalPutLatency", localMapStats.getTotalPutLatency());
                mapStats.put("totalRemoveLatency: ", localMapStats.getTotalRemoveLatency());
                mapStats.put("heapCost", localMapStats.getHeapCost());

                statsList.add(mapStats);
            });
            response.put("maps", statsList);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }

        return Collections.unmodifiableMap(response);
    }
}



