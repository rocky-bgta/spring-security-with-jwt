package com.salahin.springsecurity.service;

import com.salahin.springsecurity.entity.RoleEntity;
import com.salahin.springsecurity.entity.UserEntity;
import com.salahin.springsecurity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleOidcUserServiceTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final GoogleOidcUserService googleOidcUserService = new GoogleOidcUserService(userRepository, passwordEncoder);

	@Test
	void findOrCreateUser_whenGoogleUserDoesNotExist_createsRoleUserOnly() {
		OidcUser oidcUser = googleUser();
		when(userRepository.findByProviderAndProviderId("GOOGLE", "google-sub-123")).thenReturn(null);
		when(passwordEncoder.encode(any(String.class))).thenReturn("encoded-random-password");
		when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserEntity user = googleOidcUserService.findOrCreateUser(oidcUser);

		assertThat(user.getUsername()).isEqualTo("google_google-sub-123");
		assertThat(user.getPassword()).isEqualTo("encoded-random-password");
		assertThat(user.getProvider()).isEqualTo("GOOGLE");
		assertThat(user.getProviderId()).isEqualTo("google-sub-123");
		assertThat(user.getEmail()).isEqualTo("google.user@example.com");
		assertThat(user.getName()).isEqualTo("Google User");
		assertThat(user.isEmailVerified()).isTrue();
		assertThat(user.getRoleList()).extracting(RoleEntity::getRoleName).containsExactly("ROLE_USER");
		assertThat(user.getRoleList()).extracting(RoleEntity::getRoleName).doesNotContain("ROLE_ADMIN");
	}

	@Test
	void findOrCreateUser_whenGoogleUserExists_reusesProviderSubjectInsteadOfEmailLinking() {
		OidcUser oidcUser = googleUser();
		UserEntity existingGoogleUser = new UserEntity();
		existingGoogleUser.setUsername("google_google-sub-123");
		existingGoogleUser.setProvider("GOOGLE");
		existingGoogleUser.setProviderId("google-sub-123");
		when(userRepository.findByProviderAndProviderId("GOOGLE", "google-sub-123")).thenReturn(existingGoogleUser);
		when(userRepository.save(existingGoogleUser)).thenReturn(existingGoogleUser);

		UserEntity user = googleOidcUserService.findOrCreateUser(oidcUser);

		assertThat(user).isSameAs(existingGoogleUser);
		assertThat(user.getEmail()).isEqualTo("google.user@example.com");
		verify(userRepository).findByProviderAndProviderId("GOOGLE", "google-sub-123");
	}

	private OidcUser googleUser() {
		OidcUser oidcUser = mock(OidcUser.class);
		when(oidcUser.getSubject()).thenReturn("google-sub-123");
		when(oidcUser.getEmail()).thenReturn("google.user@example.com");
		when(oidcUser.getFullName()).thenReturn("Google User");
		when(oidcUser.getEmailVerified()).thenReturn(true);
		return oidcUser;
	}
}
