package ee.ria.eidas.client.webapp.logging;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Base64;

public class IncidentLoggingMDCServletFilter implements Filter {

    public static final String MDC_ATTRIBUTE_REQUEST = "request";
    public static final String MDC_ATTRIBUTE_REQUEST_ID = "requestId";
    public static final String MDC_ATTRIBUTE_SESSION_ID = "sessionId";

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private static final char[] REQUEST_ID_CHARACTER_SET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private static final Logger LOGGER = LoggerFactory.getLogger(IncidentLoggingMDCServletFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        try {
            final HttpServletRequest request = (HttpServletRequest) servletRequest;

            addContextAttribute(MDC_ATTRIBUTE_REQUEST, getRequestMethodAndUrl(request));
            addContextAttribute(MDC_ATTRIBUTE_REQUEST_ID, getRequestRequestId(request));
            addContextAttribute(MDC_ATTRIBUTE_SESSION_ID, getRequestSessionId(request));

            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void destroy() {
    }

    private static void addContextAttribute(final String attributeName, final Object value) {
        if (value != null && StringUtils.isNotBlank(value.toString())) {
            MDC.put(attributeName, value.toString());
        }
    }

    private static String getRequestMethodAndUrl(HttpServletRequest request) {
        return request.getMethod() + ' ' + request.getRequestURL();
    }

    private static String getRequestRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId != null) return requestId;

        requestId = RandomStringUtils.random(16, REQUEST_ID_CHARACTER_SET);
        LOGGER.debug("No " + REQUEST_ID_HEADER + " header provided in request, generated: " + requestId);
        return requestId;
    }

    private static String getRequestSessionId(HttpServletRequest request) {
        String sessionId = request.getHeader(CORRELATION_ID_HEADER);
        if (sessionId != null) return sessionId;

        sessionId = getSessionIdHash(request.getSession(true));
        LOGGER.debug("No " + CORRELATION_ID_HEADER + " header provided in request, generated: " + sessionId);
        return sessionId;
    }

    private static String getSessionIdHash(HttpSession session) {
        final byte[] sessionIdHashBytes = DigestUtils.sha256(session.getId());
        return Base64.getUrlEncoder().encodeToString(sessionIdHashBytes);
    }

}
