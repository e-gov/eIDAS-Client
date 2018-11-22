package ee.ria.eidas.client.webapp.status;

import com.hazelcast.cluster.ClusterState;
import com.hazelcast.core.HazelcastInstance;
import ee.ria.eidas.client.config.EidasClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.AbstractEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@ConfigurationProperties(
        prefix = "endpoints.heartbeat"
)
public class HeartbeatEndpoint extends AbstractEndpoint<Map<String, Object>> {

    public static final String RESPONSE_PARAM_NAME = "name";
    public static final String RESPONSE_PARAM_VERSION = "version";
    public static final String RESPONSE_PARAM_BUILD_TIME = "buildTime";
    public static final String RESPONSE_PARAM_START_TIME = "startTime";
    public static final String RESPONSE_PARAM_CURRENT_TIME = "currentTime";
    public static final String RESPONSE_PARAM_DEPENDENCIES = "dependencies";
    public static final String RESPONSE_PARAM_STATUS = "status";
    public static final String NOT_AVAILABLE = "N/A";
    public static final String DEPENDENCY_NAME_EIDAS_NODE = "eIDAS-Node";
    public static final String DEPENDENCY_NAME_HAZELCAST = "hazelcast";

    private CloseableHttpClient httpClient;
    private int timeout = 3;
    private String appName;
    private String appVersion;
    private Instant buildTime;
    private Instant startTime;
    private String idpMetadataUrl;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private EidasClientProperties properties;

    public HeartbeatEndpoint() {
        super("heartbeat", false);
    }

    @PostConstruct
    public void setUp() {
        setApplicationBuildProperties(context);
        startTime = getCurrentTime();
        idpMetadataUrl = properties.getIdpMetadataUrl();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeout * 1000)
                .setConnectionRequestTimeout(timeout * 1000)
                .setSocketTimeout(timeout * 1000)
                .build();

        httpClient = HttpClientBuilder.create()
                .disableAutomaticRetries()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @PreDestroy
    public void preDestroy() {
        HttpClientUtils.closeQuietly(httpClient);
    }

    @Override
    public Map<String, Object> invoke() {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put(RESPONSE_PARAM_NAME, formatValue(appName));
        response.put(RESPONSE_PARAM_VERSION, formatValue(appVersion));
        response.put(RESPONSE_PARAM_BUILD_TIME, formatValue(formatTime(buildTime)));
        response.put(RESPONSE_PARAM_START_TIME, formatValue(formatTime(startTime)));
        response.put(RESPONSE_PARAM_CURRENT_TIME, formatValue(formatTime(getCurrentTime())));

        List<Map<String, Object>> dependentSystemStatuses = getListOfDependencies();
        response.put(RESPONSE_PARAM_DEPENDENCIES, formatValue(dependentSystemStatuses));

        Status overallStatus = getOverallSystemStatus( dependentSystemStatuses );
        response.put(RESPONSE_PARAM_STATUS, formatValue(formatStatus( overallStatus )));

        return Collections.unmodifiableMap(response);
    }

    private Status getOverallSystemStatus(List<Map<String, Object>> dependentSystemStatuses) {
        for (Map<String, Object> system : dependentSystemStatuses) {
            Map.Entry<String, Object> entry = system.entrySet().iterator().next();
            if ( entry != null && !entry.getValue().equals(Status.UP.getCode()) ) {
                return new Status(entry.getValue().toString());
            }
        }
        return Status.UP;
    }

    private List<Map<String, Object>> getListOfDependencies() {
        List<Map<String, Object>> dependenciesList = new ArrayList();

        Status dependencyStatusEidasNode = isIdpMetadataEndpointReachableAndOk() ? Status.UP : Status.DOWN;
        dependenciesList.add(asMap(dependencyStatusEidasNode, DEPENDENCY_NAME_EIDAS_NODE));

        if (properties.isHazelcastEnabled()) {
            Status dependencyStatusHazelcast = isHazelcastUpAndRunning(context) ? Status.UP : Status.DOWN;
            dependenciesList.add(asMap(dependencyStatusHazelcast, DEPENDENCY_NAME_HAZELCAST));
        }
        return dependenciesList;
    }

    private boolean isHazelcastUpAndRunning(ApplicationContext context) {
        try {
            HazelcastInstance hazelcast = context.getBean(HazelcastInstance.class);
            return hazelcast != null && hazelcast.getCluster().getClusterState() == ClusterState.ACTIVE;
        } catch (Exception e) {
            return false;
        }
    }

    private void setApplicationBuildProperties(ApplicationContext context) {
        BuildProperties buildProperties = context.getBean(BuildProperties.class);

        appName = buildProperties.getName();
        appVersion = buildProperties.getVersion();
        buildTime = getDateAsInstant(buildProperties.getTime());
    }

    private boolean isIdpMetadataEndpointReachableAndOk() {
        HttpGet httpGet = new HttpGet(idpMetadataUrl);
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            return (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        } catch (IOException e) {
            log.error("Failed to establish connection to '{}' > {}", idpMetadataUrl, e.getMessage());
            return false;
        }
    }

    private static Instant getCurrentTime() {
        return Instant.now();
    }

    private static Instant getDateAsInstant(Date date) {
        return (date != null) ? date.toInstant() : null;
    }

    private static Object formatTime(Instant instant) {
        return (instant != null) ? instant.getEpochSecond() : null;
    }

    private static Object formatStatus(Status status) {
        return (status != null) ? status.getCode() : null;
    }

    private static Object formatValue(Object value) {
        return (value != null) ? value : NOT_AVAILABLE;
    }

    private static Map<String, Object> asMap(Status status, String name) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(RESPONSE_PARAM_STATUS, formatValue(formatStatus(status)));
        map.put(RESPONSE_PARAM_NAME, formatValue(name));
        return Collections.unmodifiableMap(map);
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
