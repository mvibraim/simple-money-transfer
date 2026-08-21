package com.example.simple_money_transfers.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiKeyAuthFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "X-API-Key";

	private final AppProperties appProperties;

	public ApiKeyAuthFilter(AppProperties appProperties) {
		this.appProperties = appProperties;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String presentedKey = request.getHeader(HEADER_NAME);
		if (presentedKey != null) {
			matchingClientId(presentedKey).ifPresent(clientId -> {
				var authentication = new UsernamePasswordAuthenticationToken(clientId, null, List.of());
				SecurityContextHolder.getContext().setAuthentication(authentication);
			});
		}
		filterChain.doFilter(request, response);
	}

	private java.util.Optional<String> matchingClientId(String presentedKey) {
		byte[] presented = presentedKey.getBytes(StandardCharsets.UTF_8);
		return appProperties.getApiKeys()
			.stream()
			.filter(entry -> MessageDigest.isEqual(presented, entry.key().getBytes(StandardCharsets.UTF_8)))
			.map(ApiKeyEntry::id)
			.findFirst();
	}

}
