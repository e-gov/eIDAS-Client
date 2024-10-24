package ee.ria.eidas.client.webapp.status;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

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
                .setConnectTimeout(Timeout.ofSeconds(timeout))
                .setConnectionRequestTimeout(Timeout.ofSeconds(timeout))
                .build();

        SocketConfig socketConfig = SocketConfig.custom().setSoTimeout(Timeout.ofSeconds(timeout)).build();

        httpClient = HttpClientBuilder.create()
                .disableAutomaticRetries()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultSocketConfig(socketConfig)
                        .build())
                .build();
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        HttpGet httpGet = new HttpGet(idpMetadataUrl);
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            if (response.getCode() == HttpStatus.SC_OK) {
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
