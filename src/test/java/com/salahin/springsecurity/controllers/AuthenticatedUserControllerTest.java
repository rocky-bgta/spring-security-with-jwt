package com.salahin.springsecurity.controllers;

import com.salahin.springsecurity.configuration.CustomJwtAuthenticationFilter;
import com.salahin.springsecurity.configuration.CustomUserDetailsService;
import com.salahin.springsecurity.configuration.GoogleOAuth2AuthenticationFailureHandler;
import com.salahin.springsecurity.configuration.GoogleOAuth2AuthenticationSuccessHandler;
import com.salahin.springsecurity.configuration.JwtAuthenticationEntryPoint;
import com.salahin.springsecurity.configuration.JwtTokenUtil;
import com.salahin.springsecurity.configuration.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthenticatedUserController.class)
@Import({
	SecurityConfiguration.class,
	CustomJwtAuthenticationFilter.class,
	JwtAuthenticationEntryPoint.class,
	JwtTokenUtil.class
})
@TestPropertySource(properties = {
	"jwt.secret=javainuse",
	"jwt.expirationDateInMs=800000",
	"jwt.refreshExpirationDateInMs=9000000"
})
class AuthenticatedUserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenUtil jwtTokenUtil;

	@MockBean
	private CustomUserDetailsService customUserDetailsService;

	@MockBean
	private GoogleOAuth2AuthenticationSuccessHandler googleOAuth2AuthenticationSuccessHandler;

	@MockBean
	private GoogleOAuth2AuthenticationFailureHandler googleOAuth2AuthenticationFailureHandler;

	@Test
	void getSecureInfo_withoutJwt_returnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/secure/info"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void getSecureInfo_withUserJwt_returnsOk() throws Exception {
		UserDetails user = User.withUsername("normal-user")
			.password("password")
			.authorities("ROLE_USER")
			.build();
		String token = jwtTokenUtil.generateToken(user);
		when(customUserDetailsService.loadUserByUsername(user.getUsername())).thenReturn(user);

		mockMvc.perform(get("/api/secure/info")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("You can see this because you are authenticated"))
			.andExpect(jsonPath("$.username").value("normal-user"));
	}

	@Test
	void getSecureInfo_withAdminJwt_returnsOk() throws Exception {
		UserDetails admin = User.withUsername("admin-user")
			.password("password")
			.authorities("ROLE_ADMIN")
			.build();
		String token = jwtTokenUtil.generateToken(admin);
		when(customUserDetailsService.loadUserByUsername(admin.getUsername())).thenReturn(admin);

		mockMvc.perform(get("/api/secure/info")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("You can see this because you are authenticated"))
			.andExpect(jsonPath("$.username").value("admin-user"));
	}
}
