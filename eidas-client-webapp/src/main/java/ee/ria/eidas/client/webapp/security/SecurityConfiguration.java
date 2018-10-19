package ee.ria.eidas.client.webapp.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.HashMap;

@Configuration
public class SecurityConfiguration {

    @Autowired
    private Environment environment;

    @Bean
    @ConditionalOnProperty("security.allowedAuthenticationPort")
    public FilterRegistrationBean authenticationPortFilter() {
        final int allowedPort = Integer.parseInt(
                environment.getProperty("security.allowedAuthenticationPort")
        );
        if (allowedPort < 1 || allowedPort > 65535) {
            throw new IllegalStateException("Illegal port number " + allowedPort);
        }

        final FilterRegistrationBean bean = new FilterRegistrationBean();
        bean.setFilter(new AuthenticationPortFilter(allowedPort));
        bean.setUrlPatterns(Arrays.asList("/login", "/returnUrl"));
        bean.setInitParameters(new HashMap<>());
        bean.setName("authenticationPortFilter");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);

        return bean;
    }

}
