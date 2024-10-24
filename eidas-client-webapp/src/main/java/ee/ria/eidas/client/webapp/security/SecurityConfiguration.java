package ee.ria.eidas.client.webapp.security;

import ee.ria.eidas.client.webapp.EidasClientApi;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.springframework.util.StringUtils.tokenizeToStringArray;

@Configuration
public class SecurityConfiguration {

    public static final String SECURITY_DISABLED_HTTP_METHODS = "security.disabled-http-methods";
    public static final String SECURITY_ALLOWED_AUTHENTICATION_PORT = "security.allowed-authentication-port";

    @Bean
    @ConditionalOnProperty(value = SECURITY_DISABLED_HTTP_METHODS)
    public OncePerRequestFilter disableHttpMethodsFilter(Environment environment) {
        String property = environment.getRequiredProperty(SECURITY_DISABLED_HTTP_METHODS);
        List list = Collections.unmodifiableList(asList(tokenizeToStringArray(property, ",")));
        Collection<String> invalidHttpMethods = CollectionUtils.subtract(list, Stream.of(HttpMethod.values())
                .map(HttpMethod::name)
                .collect(Collectors.toList()));
        if (invalidHttpMethods.isEmpty()) {
            return new DisableHttpMethodsFilter(list);
        } else {
            throw new IllegalArgumentException("Please check your configuration. Invalid value for configuration property - " + SECURITY_ALLOWED_AUTHENTICATION_PORT +". " +
                    "Invalid HTTP method: " + invalidHttpMethods
                    + ", accepted HTTP methods are: " + asList(HttpMethod.values()));
        }
    }

    @Bean
    @ConditionalOnProperty(SECURITY_ALLOWED_AUTHENTICATION_PORT)
    public FilterRegistrationBean authenticationPortFilter(Environment environment) {
        final int allowedPort = Integer.parseInt(
                environment.getProperty(SECURITY_ALLOWED_AUTHENTICATION_PORT)
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
