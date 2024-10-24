package ee.ria.eidas.client.webapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.web.filter.OncePerRequestFilter;

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
