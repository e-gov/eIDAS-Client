package ee.ria.eidas.client.webapp.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.io.IOException;
import java.util.Base64;
import java.util.function.BiConsumer;

public class LoggingMDCServletFilterTest {

    private static final String REQUEST_ID_REGEX = "[A-Z0-9]{16}";

    private LoggingMDCServletFilter servletFilter;

    @Before
    public void setUp() {
        servletFilter = new LoggingMDCServletFilter();
    }

    @Test
    public void doFilterShouldGenerateRequestAttributeIntoMdc() throws IOException, ServletException {
        final MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/some_uri");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        request.setScheme("https");
        request.setServerName("some_server_name");
        request.setServerPort(12345);

        FilterChain filterChain = mockFilterChain(request, response, (req, resp) -> {
            Assert.assertEquals("PUT https://some_server_name:12345/some_uri", MDC.get("request"));
            verifyAttributePresenceInMDC("requestId", "sessionId");
        });

        servletFilter.doFilter(request, response, filterChain);
        verifyMDCIsEmpty();
    }

    @Test
    public void doFilterShouldGenerateRequestIdIntoMdcIfNotProvided() throws IOException, ServletException {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = mockFilterChain(request, response, (req, resp) -> {
            String requestId = MDC.get("requestId");
            Assert.assertTrue(
                    String.format("Expected requestId to match \"%s\", but found \"%s\"!", REQUEST_ID_REGEX, requestId),
                    requestId.matches(REQUEST_ID_REGEX));
            verifyAttributePresenceInMDC("request", "sessionId");
        });

        servletFilter.doFilter(request, response, filterChain);
        verifyMDCIsEmpty();
    }

    @Test
    public void doFilterShouldGetRequestIdFromHeaderAndPutIntoMdc() throws IOException, ServletException {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();

        request.addHeader("X-Request-ID", "someRequestIdValue");

        FilterChain filterChain = mockFilterChain(request, response, (req, resp) -> {
            Assert.assertEquals("someRequestIdValue", MDC.get("requestId"));
            verifyAttributePresenceInMDC("request", "sessionId");
        });

        servletFilter.doFilter(request, response, filterChain);
        verifyMDCIsEmpty();
    }

    @Test
    public void doFilterShouldGenerateSessionIdHashFromSessionIntoMdc() throws IOException, ServletException {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();

        request.setSession(new MockHttpSession(null, "someSessionIdValue"));
        final String sessionIdHash = Base64.getUrlEncoder().encodeToString(DigestUtils.sha256("someSessionIdValue"));

        FilterChain filterChain = mockFilterChain(request, response, (req, resp) -> {
            Assert.assertEquals(sessionIdHash, MDC.get("sessionId"));
            verifyAttributePresenceInMDC("request", "requestId");
        });

        servletFilter.doFilter(request, response, filterChain);
        verifyMDCIsEmpty();
    }

    @Test
    public void doFilterShouldGenerateSessionAndSessionIdHashIntoMdc() throws IOException, ServletException {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();

        request.setSession(null);

        FilterChain filterChain = mockFilterChain(request, response, (req, resp) -> {
            final HttpSession session = request.getSession(false);
            Assert.assertNotNull("Session cannot be null!", session);

            final String sessionId = session.getId();
            final String sessionIdHash = Base64.getUrlEncoder().encodeToString(DigestUtils.sha256(sessionId));
            Assert.assertEquals(sessionIdHash, MDC.get("sessionId"));

            verifyAttributePresenceInMDC("request", "requestId");
        });

        servletFilter.doFilter(request, response, filterChain);
        verifyMDCIsEmpty();
    }

    @Test
    public void doFilterShouldGetCorrelationIdFromHeaderAndPutIntoMdc() throws IOException, ServletException {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();

        request.addHeader("X-Correlation-ID", "someCorrelationIdValue");

        FilterChain filterChain = mockFilterChain(request, response, (req, resp) -> {
            Assert.assertEquals("someCorrelationIdValue", MDC.get("sessionId"));
            verifyAttributePresenceInMDC("request", "requestId");
        });

        servletFilter.doFilter(request, response, filterChain);
        verifyMDCIsEmpty();
    }

    private static FilterChain mockFilterChain(final ServletRequest request, final ServletResponse response,
                                               final BiConsumer<ServletRequest, ServletResponse>... tests)
            throws IOException, ServletException {

        FilterChain filterChain = Mockito.mock(FilterChain.class);
        Mockito.doAnswer((Answer) invocation -> {

            for (BiConsumer<ServletRequest, ServletResponse> test : tests)
                test.accept(request, response);

            return null;
        }).when(filterChain).doFilter(request, response);

        return filterChain;
    }

    private static void verifyAttributePresenceInMDC(String... attributes) {
        for (String attribute : attributes)
            Assert.assertNotNull("'" + attribute + "' cannot be missing in MDC!", MDC.get(attribute));
    }

    private static void verifyMDCIsEmpty() {
        Assert.assertTrue(
                "MDC was expected to be empty, but content found!",
                MDC.getCopyOfContextMap() == null || MDC.getCopyOfContextMap().isEmpty()
        );
    }

}
