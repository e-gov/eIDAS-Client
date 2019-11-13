package ee.ria.eidas.client.webapp.security;

import ee.ria.eidas.client.webapp.EidasClientApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfiguration {

    @Bean
    @ConditionalOnProperty("security.allowed-authentication-port")
    public FilterRegistrationBean authenticationPortFilter(Environment environment) {
        final int allowedPort = Integer.parseInt(
                environment.getProperty("security.allowed-authentication-port")
        );
        if (allowedPort < 1 || allowedPort > 65535) {
            throw new IllegalStateException("Illegal port number " + allowedPort);
        }

        final FilterRegistrationBean bean = new FilterRegistrationBean();
        bean.setFilter(new AuthenticationPortFilter(allowedPort));
        bean.setInitParameters(new HashMap<>());
        bean.setName("authenticationPortFilter");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        bean.setUrlPatterns(
                Arrays.stream(EidasClientApi.Endpoint.values())
                        .filter(ep -> ep.getType() == EidasClientApi.Endpoint.Type.AUTHENTICATION)
                        .map(ep -> ep.getUrlPattern())
                        .collect(Collectors.toList())
        );

        return bean;
    }

}
