package com.salahin.springsecurity.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
	
	private final CustomUserDetailsService customUserDetailsService;
	private final CustomJwtAuthenticationFilter customJwtAuthenticationFilter;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
	
	public SecurityConfiguration(
		CustomUserDetailsService customUserDetailsService, CustomJwtAuthenticationFilter customJwtAuthenticationFilter,
		JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
		this.customUserDetailsService = customUserDetailsService;
		this.customJwtAuthenticationFilter = customJwtAuthenticationFilter;
		this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
	}
	
	@Bean
	public PasswordEncoder passwordEncoder(){
		return new BCryptPasswordEncoder();
	}
	
	@Bean
	public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
		authenticationProvider.setUserDetailsService(customUserDetailsService);
		authenticationProvider.setPasswordEncoder(passwordEncoder);
		return authenticationProvider;
	}
	
	@Bean
	public AuthenticationManager authenticationManager(DaoAuthenticationProvider authenticationProvider) {
		return new ProviderManager(authenticationProvider);
	}
	
	@Bean
	public SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		DaoAuthenticationProvider authenticationProvider,
		GoogleOAuth2AuthenticationSuccessHandler googleOAuth2AuthenticationSuccessHandler,
		GoogleOAuth2AuthenticationFailureHandler googleOAuth2AuthenticationFailureHandler) throws Exception {
		http.authenticationProvider(authenticationProvider)
			.csrf().disable()
			.formLogin().disable()
			.httpBasic().disable()
			.authorizeRequests()
			.antMatchers("/authenticate", "/register", "/oauth2/**", "/login/oauth2/**").permitAll()
			.antMatchers("/api/secure/**").authenticated()
			.antMatchers("/admin-user").hasRole("ADMIN")
			.antMatchers("/normal-user").hasAnyRole("ADMIN","USER")
			.anyRequest().authenticated()
			.and()
			.oauth2Login()
			.successHandler(googleOAuth2AuthenticationSuccessHandler)
			.failureHandler(googleOAuth2AuthenticationFailureHandler)
			.and()
			.exceptionHandling()
			.authenticationEntryPoint(jwtAuthenticationEntryPoint)
			.and()
			.sessionManagement()
			.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
			.and()
			.addFilterBefore(customJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
