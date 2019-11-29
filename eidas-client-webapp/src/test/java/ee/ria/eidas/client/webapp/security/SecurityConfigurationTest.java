package ee.ria.eidas.client.webapp.security;

import io.restassured.http.Method;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.Arrays;
import java.util.Collections;
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
    public void disableHttpMethodsFilterReturnsInstanceWhenNoConfigValues() {
        Environment environment = Mockito.mock(Environment.class);
        OncePerRequestFilter bean = configuration.disableHttpMethodsFilter(environment);
        Assert.assertTrue(DisableHttpMethodsFilter.class.isInstance(bean));
        Assert.assertEquals(((DisableHttpMethodsFilter)bean).getDisabledMethods(), Collections.EMPTY_LIST);
    }

    @Test
    public void disableHttpMethodsFilterReturnsInstanceWhenValidConfigValues() {
        Environment environment = Mockito.mock(Environment.class);
        Mockito.when(environment.getRequiredProperty(Mockito.eq(SecurityConfiguration.SECURITY_DISABLED_HTTP_METHODS))).thenReturn("    OPTIONS, TRACE , HEAD,PUT,DELETE   ");
        OncePerRequestFilter bean = configuration.disableHttpMethodsFilter(environment);
        Assert.assertTrue(DisableHttpMethodsFilter.class.isInstance(bean));
        Assert.assertEquals(((DisableHttpMethodsFilter)bean).getDisabledMethods(), Collections.unmodifiableList(Arrays.asList(Method.OPTIONS.name(), Method.TRACE.name(), Method.HEAD.name(), Method.PUT.name(), Method.DELETE.name())));

    }

    @Test
    public void disableHttpMethodsFilterThrowsExceptionWithInvalidValue() {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Please check your configuration. Invalid value for configuration property - security.allowed-authentication-port. Invalid HTTP method: [ABCDE], accepted HTTP methods are: [GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE]");

        Environment environment = Mockito.mock(Environment.class);
        Mockito.when(environment.getRequiredProperty(Mockito.eq(SecurityConfiguration.SECURITY_DISABLED_HTTP_METHODS))).thenReturn("ABCDE");
        OncePerRequestFilter bean = configuration.disableHttpMethodsFilter(environment);
        Assert.assertTrue(DisableHttpMethodsFilter.class.isInstance(bean));
        Assert.assertEquals(((DisableHttpMethodsFilter)bean).getDisabledMethods(), Collections.EMPTY_LIST);
    }


    @Test
    public void authenticationPortFilterShouldReturnValidFilter() {
        Mockito.doReturn("12345").when(environment)
                .getProperty("security.allowed-authentication-port");

        FilterRegistrationBean bean = configuration.authenticationPortFilter(environment);
        Assert.assertTrue(AuthenticationPortFilter.class.isInstance(bean.getFilter()));
        Assert.assertEquals(new HashSet<>(Arrays.asList("/login", "/returnUrl")), bean.getUrlPatterns());
        Assert.assertEquals(Ordered.HIGHEST_PRECEDENCE + 2, bean.getOrder());
    }

    @Test
    public void authenticationPortFilterShouldFailWhenAllowedPortIsZero() {
        Mockito.doReturn("0").when(environment)
                .getProperty("security.allowed-authentication-port");

        expectedEx.expect(IllegalStateException.class);
        expectedEx.expectMessage("Illegal port number 0");

        configuration.authenticationPortFilter(environment);
    }

    @Test
    public void authenticationPortFilterShouldFailWhenAllowedPortIsNegative() {
        Mockito.doReturn("-1").when(environment)
                .getProperty("security.allowed-authentication-port");

        expectedEx.expect(IllegalStateException.class);
        expectedEx.expectMessage("Illegal port number -1");

        configuration.authenticationPortFilter(environment);
    }

    @Test
    public void authenticationPortFilterShouldFailWhenAllowedPortIsAbove65535() {
        Mockito.doReturn("65536").when(environment)
                .getProperty("security.allowed-authentication-port");

        expectedEx.expect(IllegalStateException.class);
        expectedEx.expectMessage("Illegal port number 65536");

        configuration.authenticationPortFilter(environment);
    }

    @Test
    public void authenticationPortFilterShouldFailWhenAllowedPortIsNotANumber() {
        Mockito.doReturn("abc").when(environment)
                .getProperty("security.allowed-authentication-port");

        expectedEx.expect(NumberFormatException.class);
        expectedEx.expectMessage("For input string: \"abc\"");

        configuration.authenticationPortFilter(environment);
    }

}