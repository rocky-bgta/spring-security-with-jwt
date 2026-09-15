package com.salahin.springsecurity.repository;

import com.salahin.springsecurity.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
	UserEntity findByUsername(String username);

	UserEntity findByProviderAndProviderId(String provider, String providerId);
}
