package com.salahin.springsecurity.controllers;

import com.salahin.springsecurity.configuration.CustomUserDetailsService;
import com.salahin.springsecurity.configuration.JwtTokenUtil;
import com.salahin.springsecurity.model.AuthenticationRequest;
import com.salahin.springsecurity.model.AuthenticationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private CustomUserDetailsService userDetailsService;

	@Mock
	private JwtTokenUtil jwtTokenUtil;

	@InjectMocks
	private AuthenticationController authenticationController;

	@Test
	void createAuthenticationToken_withUsernameAndPassword_returnsExistingApplicationJwt() throws Exception {
		UserDetails userDetails = User.withUsername("tuli").password("password").authorities("ROLE_USER").build();
		when(userDetailsService.loadUserByUsername("tuli")).thenReturn(userDetails);
		when(jwtTokenUtil.generateToken(userDetails)).thenReturn("application-jwt");

		ResponseEntity<?> response = authenticationController.createAuthenticationToken(
			new AuthenticationRequest("tuli", "tuli"));

		assertThat(response.getBody()).isInstanceOf(AuthenticationResponse.class);
		assertThat(((AuthenticationResponse) response.getBody()).getToken()).isEqualTo("application-jwt");
		verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
		verify(jwtTokenUtil).generateToken(userDetails);
	}
}
