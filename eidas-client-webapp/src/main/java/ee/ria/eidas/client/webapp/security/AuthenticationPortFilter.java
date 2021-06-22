package ee.ria.eidas.client.webapp.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * This filter allows to limit access (to certain URL patterns) via one specific port.
 * When incoming request's local port number equals the allowed port number, then the processing of the incoming request is continued as if this filter didn't exist.
 * When incoming request's local port number is anything else but the allowed port number, then the further processing of the incoming request is stopped
 * and a 403 response, with a simple JSON object describing the error, is sent.
 */
@Slf4j
public class AuthenticationPortFilter implements Filter {

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
        log.error(message);

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
