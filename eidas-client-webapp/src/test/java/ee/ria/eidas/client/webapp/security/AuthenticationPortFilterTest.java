package ee.ria.eidas.client.webapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;


public class AuthenticationPortFilterTest {

    private static final int TEST_PORT_NUMBER = 12345;

    private AuthenticationPortFilter filter;

    @Before
    public void setUp() {
        this.filter = new AuthenticationPortFilter(TEST_PORT_NUMBER);
    }

    @After
    public void cleanUp() {
        this.filter.destroy();
        this.filter = null;
    }

    @Test
    public void doFilterShouldDoNothingWhenRequestHasAllowedPort() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        Mockito.doReturn(TEST_PORT_NUMBER).when(request).getLocalPort();

        this.filter.doFilter(request, response, filterChain);

        Mockito.verifyNoInteractions(response);
        Mockito.verify(filterChain, Mockito.times(1))
                .doFilter(request, response);
    }

    @Test
    public void doFilterShouldRespondErrorWhenRequestDoesntHaveAllowedPort() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        final int portNumber = TEST_PORT_NUMBER + 1;
        Mockito.doReturn(portNumber).when(request).getLocalPort();

        this.filter.doFilter(request, response, filterChain);

        Mockito.verifyNoInteractions(filterChain);
        verifyErroneousResponse(response, portNumber);
    }

    private static void verifyErroneousResponse(final MockHttpServletResponse response, final int portNumber) throws Exception {
        Assert.assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
        Assert.assertEquals("application/json;charset=UTF-8", response.getContentType());

        JSONAssert.assertEquals(
                String.format("{" +
                        "\"error\": \"Forbidden\", " +
                        "\"message\": \"Endpoint not allowed to be accessed via port number %d\"" +
                        "}", portNumber),
                response.getContentAsString(),
                true
        );
    }

}
