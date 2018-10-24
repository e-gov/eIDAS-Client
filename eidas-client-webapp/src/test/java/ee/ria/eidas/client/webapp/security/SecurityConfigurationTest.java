package ee.ria.eidas.client.webapp.security;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.HashSet;

@RunWith(MockitoJUnitRunner.class)
public class SecurityConfigurationTest {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Mock
    private Environment environment;

    @Autowired
    @InjectMocks
    private SecurityConfiguration configuration;

    @Before
    public void setUp() {
        Mockito.reset(environment);
    }

    @Test
    public void authenticationPortFilterShouldReturnValidFilter() {
        Mockito.doReturn("12345").when(environment)
                .getProperty("security.allowedAuthenticationPort");

        FilterRegistrationBean bean = configuration.authenticationPortFilter();
        Assert.assertTrue(AuthenticationPortFilter.class.isInstance(bean.getFilter()));
        Assert.assertEquals(new HashSet<>(Arrays.asList("/login", "/returnUrl")), bean.getUrlPatterns());
        Assert.assertEquals(Ordered.HIGHEST_PRECEDENCE + 2, bean.getOrder());
    }

    @Test
    public void authenticationPortFilterShouldFailWhenAllowedPortIsZero() {
        Mockito.doReturn("0").when(environment)
                .getProperty("security.allowedAuthenticationPort");

        expectedEx.expect(IllegalStateException.class);
        expectedEx.expectMessage("Illegal port number 0");

        configuration.authenticationPortFilter();
    }

    @Test
    public void authenticationPortFilterShouldFailWhenAllowedPortIsNegative() {
        Mockito.doReturn("-1").when(environment)
                .getProperty("security.allowedAuthenticationPort");

        expectedEx.expect(IllegalStateException.class);
        expectedEx.expectMessage("Illegal port number -1");

        configuration.authenticationPortFilter();
    }

    @Test
    public void authenticationPortFilterShouldFailWhenAllowedPortIsAbove65535() {
        Mockito.doReturn("65536").when(environment)
                .getProperty("security.allowedAuthenticationPort");

        expectedEx.expect(IllegalStateException.class);
        expectedEx.expectMessage("Illegal port number 65536");

        configuration.authenticationPortFilter();
    }

    @Test
    public void authenticationPortFilterShouldFailWhenAllowedPortIsNotANumber() {
        Mockito.doReturn("abc").when(environment)
                .getProperty("security.allowedAuthenticationPort");

        expectedEx.expect(NumberFormatException.class);
        expectedEx.expectMessage("For input string: \"abc\"");

        configuration.authenticationPortFilter();
    }

}