package ee.ria.eidas.client.webapp.status;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.NamedContributor;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.Double.valueOf;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Slf4j
@Component
@ConditionalOnAvailableEndpoint(endpoint = HeartbeatEndpoint.class)
@Endpoint(id = "heartbeat", enableByDefault = false)
@ConfigurationProperties(prefix = "management.endpoint.heartbeat")
public class HeartbeatEndpoint {

    public static final String RESPONSE_PARAM_NAME = "name";
    public static final String RESPONSE_PARAM_VERSION = "version";
    public static final String RESPONSE_PARAM_BUILD_TIME = "buildTime";
    public static final String RESPONSE_PARAM_START_TIME = "startTime";
    public static final String RESPONSE_PARAM_CURRENT_TIME = "currentTime";
    public static final String RESPONSE_PARAM_DEPENDENCIES = "dependencies";
    public static final String RESPONSE_PARAM_STATUS = "status";
    public static final String NOT_AVAILABLE = "N/A";

    @Autowired
    private BuildProperties buildProperties;

    @Autowired
    private HealthContributorRegistry healthContributorRegistry;

    @Autowired
    private MeterRegistry meterRegistry;

    private Long processStartTime;

    @PostConstruct
    public void setUp() {
        processStartTime = getProcessStartTime();
    }

    @ReadOperation(produces = {"application/json"})
    public Map<String, Object> getHealthInfo() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(RESPONSE_PARAM_NAME, formatValue(buildProperties.getName()));
        response.put(RESPONSE_PARAM_VERSION, formatValue(buildProperties.getVersion()));
        response.put(RESPONSE_PARAM_BUILD_TIME, formatValue(formatTime(buildProperties.getTime())));
        response.put(RESPONSE_PARAM_START_TIME, formatValue(processStartTime));
        response.put(RESPONSE_PARAM_CURRENT_TIME, formatTime(getCurrentTime()));

        Map<String, Status> dependentSystemStatuses = getHealthIndicatorStatuses();
        response.put(RESPONSE_PARAM_DEPENDENCIES, formatStatuses(dependentSystemStatuses));

        String overallStatus = getOverallSystemStatus(dependentSystemStatuses).getCode();
        response.put(RESPONSE_PARAM_STATUS, formatValue(overallStatus));

        return Collections.unmodifiableMap(response);
    }

    private Map<String, Status> getHealthIndicatorStatuses() {
        return healthContributorRegistry.stream()
                .filter(hc -> hc.getContributor() instanceof HealthIndicator)
                .collect(toMap(NamedContributor::getName,
                        healthContributorNamedContributor -> ((HealthIndicator) healthContributorNamedContributor
                                .getContributor()).health().getStatus()));
    }

    private Status getOverallSystemStatus(Map<String, Status> healthIndicatorStatuses) {
        Optional<Status> anyNotUp = healthIndicatorStatuses.values().stream()
                .filter(status -> !Status.UP.equals(status))
                .findAny();
        return anyNotUp.isPresent() ? Status.DOWN : Status.UP;
    }

    private Long getProcessStartTime() {
        TimeGauge startTime = meterRegistry.find("process.start.time").timeGauge();
        return startTime != null ? valueOf(startTime.value(SECONDS)).longValue() : null;
    }

    private static Instant getCurrentTime() {
        return Instant.now();
    }

    private static Object formatTime(Instant instant) {
        return (instant != null) ? instant.getEpochSecond() : null;
    }

    private static Object formatValue(Object value) {
        return (value != null) ? value : NOT_AVAILABLE;
    }

    private List<Map<String, String>> formatStatuses(Map<String, Status> healthIndicatorStatuses) {
        return healthIndicatorStatuses.entrySet().stream()
                .map(healthIndicator -> {
                    Map<String, String> values = new HashMap<>();
                    values.put("name", healthIndicator.getKey());
                    values.put("status", healthIndicator.getValue().getCode());
                    return values;
                }).collect(toList());
    }
}
