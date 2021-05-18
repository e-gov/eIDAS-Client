package ee.ria.eidas.client.webapp.status;

import com.hazelcast.cluster.ClusterState;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty("eidas.client.hazelcast-enabled")
public class HazelcastHealthIndicator extends AbstractHealthIndicator {

    @Autowired
    public HazelcastInstance hazelcast;

    public HazelcastHealthIndicator() {
        super("Hazelcast health check failed");
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (hazelcast.getCluster().getClusterState() == ClusterState.ACTIVE) {
            builder.up().build();
        } else {
            builder.down().build();
        }
    }
}
