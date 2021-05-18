package ee.ria.eidas.client.config;

import com.hazelcast.core.HazelcastInstance;
import ee.ria.eidas.client.session.LocalRequestSessionServiceImpl;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@TestPropertySource(locations= "classpath:application-test-hazelcast-disabled.properties")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
        classes = {EidasClientConfiguration.class, EidasCredentialsConfiguration.class, HazelcastConfiguration.class},
        initializers = ConfigFileApplicationContextInitializer.class)
public class HazelcastDisabledConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;
    
    @Test
    public void whenConfigurationNotEnabledThenHazelcastNotInitialized() {
        assertBeanNotInitiated(HazelcastInstance.class);
        assertBeanNotInitiated(HazelcastConfiguration.class);

        Object instance = applicationContext.getBean("requestSessionService");
        Assert.assertNotNull(instance);
        Assert.assertTrue(instance instanceof LocalRequestSessionServiceImpl);
    }

    private void assertBeanNotInitiated(Class clazz) {
        try {
            applicationContext.getBean(clazz);
            Assert.fail("Bean <" + clazz + "> should not be initiated!");
        } catch (NoSuchBeanDefinitionException e) {
        }
    }
}