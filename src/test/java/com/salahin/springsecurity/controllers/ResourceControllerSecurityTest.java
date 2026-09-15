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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResourceController.class)
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
class ResourceControllerSecurityTest {

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
	void normalUser_withUserJwt_returnsOk() throws Exception {
		UserDetails user = User.withUsername("local-user").password("password").authorities("ROLE_USER").build();
		String token = jwtTokenUtil.generateToken(user);
		when(customUserDetailsService.loadUserByUsername(user.getUsername())).thenReturn(user);

		mockMvc.perform(get("/normal-user").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().string("Hello User"));
	}

	@Test
	void adminUser_withAdminJwt_returnsOk() throws Exception {
		UserDetails admin = User.withUsername("local-admin").password("password").authorities("ROLE_ADMIN").build();
		String token = jwtTokenUtil.generateToken(admin);
		when(customUserDetailsService.loadUserByUsername(admin.getUsername())).thenReturn(admin);

		mockMvc.perform(get("/admin-user").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().string("Hello Admin"));
	}

	@Test
	void adminUser_withGoogleRoleUserJwt_returnsForbidden() throws Exception {
		UserDetails googleUser = User.withUsername("google_google-sub-123")
			.password("password")
			.authorities("ROLE_USER")
			.build();
		String token = jwtTokenUtil.generateToken(googleUser);
		when(customUserDetailsService.loadUserByUsername(googleUser.getUsername())).thenReturn(googleUser);

		mockMvc.perform(get("/admin-user").header("Authorization", "Bearer " + token))
			.andExpect(status().isForbidden());
	}
}
