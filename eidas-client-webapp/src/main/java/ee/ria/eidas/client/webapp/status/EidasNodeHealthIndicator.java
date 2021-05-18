package ee.ria.eidas.client.webapp.status;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Slf4j
@Component("eIDAS-Node")
public class EidasNodeHealthIndicator extends AbstractHealthIndicator {
    private CloseableHttpClient httpClient;

    @Value("${management.endpoint.heartbeat.timeout:3}")
    private int timeout;

    @Value("${eidas.client.idp-metadata-url}")
    private String idpMetadataUrl;

    public EidasNodeHealthIndicator() {
        super("eIDAS-Node health check failed");
    }

    @PostConstruct
    public void setUp() {
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

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        HttpGet httpGet = new HttpGet(idpMetadataUrl);
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                builder.up().build();
            } else {
                builder.down().build();
            }
        } catch (IOException e) {
            log.error("Failed to establish connection to '{}' > {}", idpMetadataUrl, e.getMessage(), e);
            builder.down().build();
        }
    }
}
