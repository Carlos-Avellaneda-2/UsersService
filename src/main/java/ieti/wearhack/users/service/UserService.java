package ieti.wearhack.users.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ieti.wearhack.users.exception.UserNotFoundException;
import ieti.wearhack.users.model.User;
import ieti.wearhack.users.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public User findByEmail(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
	}

	public Optional<User> findByGoogleId(String googleId) {
		return userRepository.findByGoogleId(googleId);
	}

	public User createUser(User user) {
		return userRepository.save(user);
	}

	public User updateUser(User user) {
		if (user.getId() == null || !userRepository.existsById(user.getId())) {
			throw new UserNotFoundException("User not found with id: " + user.getId());
		}
		return userRepository.save(user);
	}

	public User getUser(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
	}

}
