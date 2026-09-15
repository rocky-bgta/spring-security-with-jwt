package com.salahin.springsecurity.service;

import com.salahin.springsecurity.entity.RoleEntity;
import com.salahin.springsecurity.entity.UserEntity;
import com.salahin.springsecurity.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GoogleOidcUserService {

	private static final String GOOGLE_PROVIDER = "GOOGLE";
	private static final String ROLE_USER = "ROLE_USER";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public GoogleOidcUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public UserEntity findOrCreateUser(OidcUser oidcUser) {
		String googleSubject = oidcUser.getSubject();
		if (googleSubject == null || googleSubject.trim().isEmpty()) {
			throw new IllegalArgumentException("Google OIDC subject is required");
		}

		UserEntity existingUser = userRepository.findByProviderAndProviderId(GOOGLE_PROVIDER, googleSubject);
		if (existingUser != null) {
			updateGoogleProfile(existingUser, oidcUser);
			return userRepository.save(existingUser);
		}

		UserEntity user = new UserEntity();
		user.setUsername(buildGoogleUsername(googleSubject));
		user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
		user.setProvider(GOOGLE_PROVIDER);
		user.setProviderId(googleSubject);
		user.setStatus(true);
		updateGoogleProfile(user, oidcUser);

		RoleEntity userRole = new RoleEntity();
		userRole.setRoleName(ROLE_USER);
		user.getRoleList().add(userRole);

		return userRepository.save(user);
	}

	private void updateGoogleProfile(UserEntity user, OidcUser oidcUser) {
		user.setEmail(oidcUser.getEmail());
		user.setName(oidcUser.getFullName());
		user.setEmailVerified(Boolean.TRUE.equals(oidcUser.getEmailVerified()));
	}

	private String buildGoogleUsername(String googleSubject) {
		return "google_" + googleSubject.replaceAll("[^a-zA-Z0-9_-]", "_");
	}
}
