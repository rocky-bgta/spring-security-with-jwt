package com.salahin.springsecurity.service.impl;

import com.salahin.springsecurity.entity.UserEntity;
import com.salahin.springsecurity.model.UserModel;
import com.salahin.springsecurity.repository.UserRepository;
import com.salahin.springsecurity.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service("UserService")
public class UserServiceImpl implements UserService {
	
	
	private final UserRepository userRepository;
	private final PasswordEncoder bcryptEncoder;
	
	public UserServiceImpl(UserRepository userRepository, PasswordEncoder bcryptEncoder) {
		this.userRepository = userRepository;
		this.bcryptEncoder = bcryptEncoder;
	}
	
	@Override
	public UserModel saveUser(UserModel userModel) {
		UserEntity savedUser = new UserEntity();
		userModel.setPassword(bcryptEncoder.encode(userModel.getPassword()));
		savedUser.setUsername(userModel.getUsername());
		savedUser.setPassword(userModel.getPassword());
		savedUser.setProvider("LOCAL");
		savedUser.setRoleList(userModel.getRoleList());
		savedUser = userRepository.save(savedUser);
		userModel.setId(savedUser.getId());
		return userModel;
	}
}
