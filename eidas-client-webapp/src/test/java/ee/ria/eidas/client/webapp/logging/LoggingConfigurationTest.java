package ee.ria.eidas.client.webapp.logging;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
        classes = LoggingConfiguration.class,
        initializers = ConfigDataApplicationContextInitializer.class)
public class LoggingConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void verifyIncidentLoggingMDCServletFilterBeanPresence() {
        Object bean = applicationContext.getBean("loggingMDCServletFilter");
        Assert.assertEquals(FilterRegistrationBean.class, bean.getClass());

        final FilterRegistrationBean filterRegistrationBean = (FilterRegistrationBean) bean;
        Assert.assertEquals(LoggingMDCServletFilter.class, filterRegistrationBean.getFilter().getClass());
        Assert.assertEquals(Collections.singleton("/*"), filterRegistrationBean.getUrlPatterns());
        Assert.assertEquals(Ordered.HIGHEST_PRECEDENCE + 1, filterRegistrationBean.getOrder());
    }

}
