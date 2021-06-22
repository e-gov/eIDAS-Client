package ee.ria.eidas.client.webapp.status;

import org.springframework.boot.actuate.health.DefaultHealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;

@Configuration
public class HealthConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HealthContributorRegistry healthContributorRegistry(ApplicationContext ctx) {
        return new DefaultHealthContributorRegistry(new LinkedHashMap<>(ctx.getBeansOfType(HealthContributor.class)));
    }
}
