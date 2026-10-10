package ieti.wearhack.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ieti.wearhack.users.exception.UserNotFoundException;
import ieti.wearhack.users.model.User;
import ieti.wearhack.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private UserService userService;

	@Test
	void findByEmailReturnsUser() {
		User user = new User("Ada", "ada@example.com", "g-1", null);
		when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

		assertThat(userService.findByEmail("ada@example.com")).isSameAs(user);
	}

	@Test
	void findByEmailThrowsWhenMissing() {
		when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.findByEmail("nobody@example.com"))
			.isInstanceOf(UserNotFoundException.class)
			.hasMessageContaining("nobody@example.com");
	}

	@Test
	void findByGoogleIdDelegatesToRepository() {
		User user = new User("Ada", "ada@example.com", "g-1", null);
		when(userRepository.findByGoogleId("g-1")).thenReturn(Optional.of(user));

		assertThat(userService.findByGoogleId("g-1")).containsSame(user);
	}

	@Test
	void createUserSavesUser() {
		User user = new User("Ada", "ada@example.com", "g-1", null);
		when(userRepository.save(user)).thenReturn(user);

		assertThat(userService.createUser(user)).isSameAs(user);
		verify(userRepository).save(user);
	}

	@Test
	void updateUserSavesExistingUser() {
		User user = new User("Ada", "ada@example.com", "g-1", null);
		user.setId(UUID.randomUUID());
		when(userRepository.existsById(user.getId())).thenReturn(true);
		when(userRepository.save(user)).thenReturn(user);

		assertThat(userService.updateUser(user)).isSameAs(user);
	}

	@Test
	void updateUserThrowsWhenUserDoesNotExist() {
		User user = new User("Ada", "ada@example.com", "g-1", null);
		user.setId(UUID.randomUUID());
		when(userRepository.existsById(user.getId())).thenReturn(false);

		assertThatThrownBy(() -> userService.updateUser(user)).isInstanceOf(UserNotFoundException.class);
		verify(userRepository, never()).save(any());
	}

	@Test
	void updateUserThrowsWhenIdIsNull() {
		User user = new User("Ada", "ada@example.com", "g-1", null);

		assertThatThrownBy(() -> userService.updateUser(user)).isInstanceOf(UserNotFoundException.class);
		verify(userRepository, never()).save(any());
	}

	@Test
	void getUserReturnsUser() {
		UUID id = UUID.randomUUID();
		User user = new User("Ada", "ada@example.com", "g-1", null);
		when(userRepository.findById(id)).thenReturn(Optional.of(user));

		assertThat(userService.getUser(id)).isSameAs(user);
	}

	@Test
	void getUserThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(userRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.getUser(id)).isInstanceOf(UserNotFoundException.class)
			.hasMessageContaining(id.toString());
	}

}
