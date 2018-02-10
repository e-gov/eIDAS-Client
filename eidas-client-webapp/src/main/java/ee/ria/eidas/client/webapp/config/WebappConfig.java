package ee.ria.eidas.client.webapp.config;

import ee.ria.eidas.config.EidasClientConfiguration;
import ee.ria.eidas.config.EidasClientProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(EidasClientConfiguration.class)
public class WebappConfig {
}
