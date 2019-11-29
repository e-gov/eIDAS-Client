package ee.ria.eidas.client.webapp.security;

import lombok.Getter;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class DisableHttpMethodsFilter extends OncePerRequestFilter {

	@Getter
	private final List<String> disabledMethods;

	public DisableHttpMethodsFilter(List<String> disabledMethods) {
		this.disabledMethods = disabledMethods;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (disabledMethods.contains(request.getMethod())) {
			response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, String.format("Request method '%s' not supported", request.getMethod()));
		} else {
			filterChain.doFilter(request, response);
		}
	}
}