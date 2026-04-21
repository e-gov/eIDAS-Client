package ee.ria.eidas.client.webapp.status;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.registry.DefaultHealthContributorRegistry;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HealthConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HealthContributorRegistry healthContributorRegistry(ApplicationContext ctx) {
        return new DefaultHealthContributorRegistry(null,
                registrations -> ctx.getBeansOfType(HealthContributor.class).forEach(registrations));
    }
}
