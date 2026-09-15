package com.salahin.springsecurity.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salahin.springsecurity.entity.UserEntity;
import com.salahin.springsecurity.service.GoogleOidcUserService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GoogleOAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

	private final GoogleOidcUserService googleOidcUserService;
	private final CustomUserDetailsService userDetailsService;
	private final JwtTokenUtil jwtTokenUtil;
	private final ObjectMapper objectMapper;

	public GoogleOAuth2AuthenticationSuccessHandler(
		GoogleOidcUserService googleOidcUserService,
		CustomUserDetailsService userDetailsService,
		JwtTokenUtil jwtTokenUtil,
		ObjectMapper objectMapper) {
		this.googleOidcUserService = googleOidcUserService;
		this.userDetailsService = userDetailsService;
		this.jwtTokenUtil = jwtTokenUtil;
		this.objectMapper = objectMapper;
	}

	@Override
	public void onAuthenticationSuccess(
		HttpServletRequest request,
		HttpServletResponse response,
		Authentication authentication) throws IOException {

		OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
		UserEntity localUser = googleOidcUserService.findOrCreateUser(oidcUser);
		UserDetails userDetails = userDetailsService.loadUserByUsername(localUser.getUsername());
		String applicationJwt = jwtTokenUtil.generateToken(userDetails);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("message", "Google login succeeded. Use this application JWT as a Bearer token in Postman.");
		body.put("tokenType", "Bearer");
		body.put("token", applicationJwt);
		body.put("username", localUser.getUsername());
		body.put("provider", localUser.getProvider());
		body.put("googleSubject", oidcUser.getSubject());
		body.put("email", oidcUser.getEmail());
		body.put("name", oidcUser.getFullName());
		body.put("emailVerified", oidcUser.getEmailVerified());
		body.put("developmentNote", "This browser response is for development/learning. Production apps should use a secure cookie or a frontend/backend token exchange.");

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(response.getWriter(), body);
	}
}
