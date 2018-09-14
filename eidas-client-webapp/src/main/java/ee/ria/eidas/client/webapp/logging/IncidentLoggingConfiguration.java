package ee.ria.eidas.client.webapp.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class IncidentLoggingConfiguration {

    @Bean
    public FilterRegistrationBean incidentLoggingMDCServletFilter() {
        final Map<String, String> initParams = new HashMap<>();
        final FilterRegistrationBean bean = new FilterRegistrationBean();
        bean.setFilter(new IncidentLoggingMDCServletFilter());
        bean.setUrlPatterns(Collections.singleton("/*"));
        bean.setInitParameters(initParams);
        bean.setName("incidentLoggingMDCServletFilter");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return bean;
    }

}
