package ieti.wearhack.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ieti.wearhack.users.config.JwtProperties;
import ieti.wearhack.users.dto.AuthResponse;
import ieti.wearhack.users.dto.GoogleProfile;
import ieti.wearhack.users.exception.InvalidGoogleTokenException;
import ieti.wearhack.users.exception.InvalidTokenException;
import ieti.wearhack.users.exception.UserNotFoundException;
import ieti.wearhack.users.model.User;
import ieti.wearhack.users.provider.GoogleAuthProvider;
import ieti.wearhack.users.security.AuthenticatedUser;
import io.jsonwebtoken.security.WeakKeyException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final String SECRET = "unit-test-secret-that-is-at-least-256-bits-long-0123456789";

	private static final String GOOGLE_TOKEN = "google-id-token";

	private static final GoogleProfile PROFILE = new GoogleProfile("g-1", "ada@example.com", "Ada Lovelace",
			"https://example.com/ada.png");

	@Mock
	private UserService userService;

	@Mock
	private GoogleAuthProvider googleAuthProvider;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(userService, googleAuthProvider, new JwtProperties(SECRET, 3_600_000L));
	}

	@Test
	void authenticateGoogleCreatesUserOnFirstLogin() {
		givenValidGoogleToken();
		when(userService.findByGoogleId("g-1")).thenReturn(Optional.empty());
		when(userService.findByEmail("ada@example.com")).thenThrow(new UserNotFoundException("not found"));
		when(userService.createUser(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			user.setId(UUID.randomUUID());
			return user;
		});

		AuthResponse response = authService.authenticateGoogle(GOOGLE_TOKEN);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userService).createUser(captor.capture());
		User created = captor.getValue();
		assertThat(created.getGoogleId()).isEqualTo("g-1");
		assertThat(created.getEmail()).isEqualTo("ada@example.com");
		assertThat(created.getName()).isEqualTo("Ada Lovelace");
		assertThat(created.getPicture()).isEqualTo("https://example.com/ada.png");
		verify(userService, never()).updateUser(any());

		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresInMs()).isEqualTo(3_600_000L);
		assertThat(response.user().id()).isEqualTo(created.getId());
		assertThat(response.user().email()).isEqualTo("ada@example.com");
		assertThat(authService.validateToken(response.token())).isTrue();
	}

	@Test
	void authenticateGoogleUpdatesNameAndPictureOfExistingUser() {
		givenValidGoogleToken();
		User existing = existingUser("Old Name", "g-1", "https://example.com/old.png");
		when(userService.findByGoogleId("g-1")).thenReturn(Optional.of(existing));
		when(userService.updateUser(existing)).thenReturn(existing);

		AuthResponse response = authService.authenticateGoogle(GOOGLE_TOKEN);

		verify(userService).updateUser(existing);
		verify(userService, never()).createUser(any());
		assertThat(existing.getName()).isEqualTo("Ada Lovelace");
		assertThat(existing.getPicture()).isEqualTo("https://example.com/ada.png");
		assertThat(response.user().name()).isEqualTo("Ada Lovelace");
	}

	@Test
	void authenticateGoogleLinksGoogleIdWhenUserFoundByEmail() {
		givenValidGoogleToken();
		User existing = existingUser("Ada", null, null);
		when(userService.findByGoogleId("g-1")).thenReturn(Optional.empty());
		when(userService.findByEmail("ada@example.com")).thenReturn(existing);
		when(userService.updateUser(existing)).thenReturn(existing);

		authService.authenticateGoogle(GOOGLE_TOKEN);

		assertThat(existing.getGoogleId()).isEqualTo("g-1");
		verify(userService, never()).createUser(any());
	}

	@Test
	void authenticateGoogleRejectsInvalidGoogleToken() {
		when(googleAuthProvider.verifyGoogleToken("bad")).thenReturn(false);

		assertThatThrownBy(() -> authService.authenticateGoogle("bad"))
			.isInstanceOf(InvalidGoogleTokenException.class);
		verify(googleAuthProvider, never()).getGoogleProfile(any());
		verifyNoInteractions(userService);
	}

	@Test
	void authenticateGoogleWithCodeExchangesCodeFirst() {
		when(googleAuthProvider.exchangeCodeForIdToken("auth-code")).thenReturn(GOOGLE_TOKEN);
		givenValidGoogleToken();
		User existing = existingUser("Ada", "g-1", null);
		when(userService.findByGoogleId("g-1")).thenReturn(Optional.of(existing));
		when(userService.updateUser(existing)).thenReturn(existing);

		AuthResponse response = authService.authenticateGoogleWithCode("auth-code");

		verify(googleAuthProvider).exchangeCodeForIdToken("auth-code");
		assertThat(authService.validateToken(response.token())).isTrue();
	}

	@Test
	void buildGoogleAuthorizationUrlDelegatesToProvider() {
		when(googleAuthProvider.buildAuthorizationUrl("state-1")).thenReturn("https://accounts.google.com/x");

		assertThat(authService.buildGoogleAuthorizationUrl("state-1")).isEqualTo("https://accounts.google.com/x");
	}

	@Test
	void generatedJwtCarriesUserIdAndEmail() {
		User existing = loginExistingUser();
		String token = authService.authenticateGoogle(GOOGLE_TOKEN).token();

		AuthenticatedUser authenticated = authService.getAuthenticatedUser(token);

		assertThat(authenticated.id()).isEqualTo(existing.getId());
		assertThat(authenticated.email()).isEqualTo("ada@example.com");
	}

	@Test
	void validateTokenRejectsMalformedTokens() {
		assertThat(authService.validateToken(null)).isFalse();
		assertThat(authService.validateToken("")).isFalse();
		assertThat(authService.validateToken("not-a-jwt")).isFalse();
	}

	@Test
	void validateTokenRejectsTokenSignedWithAnotherKey() {
		loginExistingUser();
		AuthService other = new AuthService(userService, googleAuthProvider,
				new JwtProperties("another-secret-that-is-also-at-least-256-bits-long-abcdef", 3_600_000L));
		String foreignToken = other.authenticateGoogle(GOOGLE_TOKEN).token();

		assertThat(authService.validateToken(foreignToken)).isFalse();
	}

	@Test
	void validateTokenRejectsExpiredToken() {
		loginExistingUser();
		AuthService expiring = new AuthService(userService, googleAuthProvider, new JwtProperties(SECRET, -60_000L));
		String expiredToken = expiring.authenticateGoogle(GOOGLE_TOKEN).token();

		assertThat(authService.validateToken(expiredToken)).isFalse();
		assertThatThrownBy(() -> authService.getAuthenticatedUser(expiredToken))
			.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void logoutBlacklistsOnlyThatToken() {
		loginExistingUser();
		String first = authService.authenticateGoogle(GOOGLE_TOKEN).token();
		String second = authService.authenticateGoogle(GOOGLE_TOKEN).token();

		authService.logout(first);

		assertThat(authService.validateToken(first)).isFalse();
		assertThat(authService.validateToken(second)).isTrue();
		assertThatThrownBy(() -> authService.logout(first)).isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void logoutRejectsInvalidToken() {
		assertThatThrownBy(() -> authService.logout("not-a-jwt")).isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void secretShorterThan256BitsIsRejected() {
		assertThatThrownBy(
				() -> new AuthService(userService, googleAuthProvider, new JwtProperties("too-short", 1000L)))
			.isInstanceOf(WeakKeyException.class);
	}

	private void givenValidGoogleToken() {
		when(googleAuthProvider.verifyGoogleToken(GOOGLE_TOKEN)).thenReturn(true);
		when(googleAuthProvider.getGoogleProfile(GOOGLE_TOKEN)).thenReturn(PROFILE);
	}

	private User loginExistingUser() {
		givenValidGoogleToken();
		User existing = existingUser("Ada", "g-1", null);
		when(userService.findByGoogleId("g-1")).thenReturn(Optional.of(existing));
		when(userService.updateUser(existing)).thenReturn(existing);
		return existing;
	}

	private User existingUser(String name, String googleId, String picture) {
		User user = new User(name, "ada@example.com", googleId, picture);
		user.setId(UUID.randomUUID());
		return user;
	}

}
