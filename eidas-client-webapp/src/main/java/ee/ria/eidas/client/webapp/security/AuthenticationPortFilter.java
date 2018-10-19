package ee.ria.eidas.client.webapp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class AuthenticationPortFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationPortFilter.class);

    private final int allowedPort;

    public AuthenticationPortFilter(final int allowedPort) {
        this.allowedPort = allowedPort;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (servletRequest.getLocalPort() == this.allowedPort) {
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            sendForbiddenResponse((HttpServletResponse) servletResponse, String.format(
                    "Endpoint not allowed to be accessed via port number %d",
                    servletRequest.getLocalPort()
            ));
        }
    }

    @Override
    public void destroy() {
    }

    private static void sendForbiddenResponse(final HttpServletResponse response, final String message) throws IOException {
        LOGGER.error(message);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");

        try (PrintWriter writer = response.getWriter()) {
            writer.format("{\"error\":\"%s\",\"message\":\"%s\"}",
                    HttpStatus.FORBIDDEN.getReasonPhrase(),
                    message
            );
            writer.flush();
        }
    }

}
