package com.salahin.springsecurity.configuration;

import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class CustomJwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtTokenUtil jwtTokenUtil;
	private final CustomUserDetailsService customUserDetailsService;
	
	public CustomJwtAuthenticationFilter(JwtTokenUtil jwtTokenUtil, CustomUserDetailsService customUserDetailsService) {
		this.jwtTokenUtil = jwtTokenUtil;
		this.customUserDetailsService = customUserDetailsService;
	}
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		
		// JWT Token is in the form "Bearer token". Remove Bearer word and
		// get  only the Token
		try {
			String jwtToken = extractJwtFromRequest(request);
			String username = jwtTokenUtil.getUsernameFromToken(jwtToken);
			
			if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
				UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
				
//				if (StringUtils.hasText(jwtToken) && jwtTokenUtil.validateToken(jwtToken)) {
//					userDetails = new User(jwtTokenUtil.getUsernameFromToken(jwtToken), "",
//						jwtTokenUtil.getRolesFromToken(jwtToken));
				if(jwtTokenUtil.validateToken(jwtToken,userDetails)){
				
					
					UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
						new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
					// After setting the Authentication in the context, we specify
					// that the current user is authenticated. So it passes the
					// Spring Security Configurations successfully.
					SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
				}
			}
			
			
			else {
				System.out.println("Cannot set the Security Context");
			}
		} catch (ExpiredJwtException ex) {
			request.setAttribute("exception", ex);
		} catch (BadCredentialsException ex) {
			request.setAttribute("exception", ex);
		}
		chain.doFilter(request, response);
	}

	private String extractJwtFromRequest(HttpServletRequest request) {
		String bearerToken = request.getHeader("Authorization");
		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7);
		}
		return null;
	}

}
