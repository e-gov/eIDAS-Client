package ee.ria.eidas.client.webapp.logging;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
        classes = IncidentLoggingConfiguration.class,
        initializers = ConfigFileApplicationContextInitializer.class)
public class IncidentLoggingConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void verifyIncidentLoggingMDCServletFilterBeanPresence() {
        Object bean = applicationContext.getBean("incidentLoggingMDCServletFilter");
        Assert.assertEquals(FilterRegistrationBean.class, bean.getClass());

        final FilterRegistrationBean filterRegistrationBean = (FilterRegistrationBean) bean;
        Assert.assertEquals(IncidentLoggingMDCServletFilter.class, filterRegistrationBean.getFilter().getClass());
        Assert.assertEquals(Collections.singleton("/*"), filterRegistrationBean.getUrlPatterns());
        Assert.assertEquals(Ordered.HIGHEST_PRECEDENCE + 1, filterRegistrationBean.getOrder());
    }

}
