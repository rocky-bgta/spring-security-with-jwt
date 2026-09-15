package com.salahin.springsecurity.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AuthenticatedUserController {

	@RequestMapping(value = "/api/secure/info", method = RequestMethod.GET)
	public ResponseEntity<?> getSecureInfo(Authentication authentication) {
		Map<String, String> response = new HashMap<>();
		response.put("message", "You can see this because you are authenticated");
		response.put("username", authentication.getName());

		return ResponseEntity.ok(response);
	}
}
