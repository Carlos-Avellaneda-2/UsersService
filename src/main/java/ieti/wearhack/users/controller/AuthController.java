package ieti.wearhack.users.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ieti.wearhack.users.dto.AuthResponse;
import ieti.wearhack.users.dto.AuthorizationUrlResponse;
import ieti.wearhack.users.dto.GoogleLoginRequest;
import ieti.wearhack.users.dto.MessageResponse;
import ieti.wearhack.users.exception.InvalidOAuthStateException;
import ieti.wearhack.users.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private static final String STATE_COOKIE = "oauth_state";

	private static final String STATE_COOKIE_PATH = "/api/auth/google";

	private static final Duration STATE_TTL = Duration.ofMinutes(10);

	private final SecureRandom secureRandom = new SecureRandom();

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	/**
	 * Returns the Google authorization URL as JSON, or redirects to it with
	 * {@code ?redirect=true}. The state is also stored in a cookie and checked on callback.
	 */
	@GetMapping("/google/login")
	public ResponseEntity<AuthorizationUrlResponse> loginWithGoogle(
			@RequestParam(defaultValue = "false") boolean redirect, HttpServletRequest request) {
		String state = generateState();
		String authorizationUrl = authService.buildGoogleAuthorizationUrl(state);
		String cookie = stateCookie(state, STATE_TTL, request).toString();

		if (redirect) {
			return ResponseEntity.status(HttpStatus.FOUND)
				.header(HttpHeaders.SET_COOKIE, cookie)
				.location(URI.create(authorizationUrl))
				.build();
		}
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, cookie)
			.body(new AuthorizationUrlResponse(authorizationUrl));
	}

	@GetMapping("/google/callback")
	public ResponseEntity<AuthResponse> callbackGoogle(@RequestParam String code, @RequestParam String state,
			@CookieValue(name = STATE_COOKIE, required = false) String expectedState, HttpServletRequest request) {
		if (expectedState == null || !MessageDigest.isEqual(expectedState.getBytes(StandardCharsets.UTF_8),
				state.getBytes(StandardCharsets.UTF_8))) {
			throw new InvalidOAuthStateException("Missing or mismatched OAuth state; start again at /google/login");
		}
		AuthResponse response = authService.authenticateGoogleWithCode(code);
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, stateCookie("", Duration.ZERO, request).toString())
			.body(response);
	}

	@PostMapping("/google")
	public AuthResponse authenticateWithGoogleIdToken(@Valid @RequestBody GoogleLoginRequest request) {
		return authService.authenticateGoogle(request.idToken());
	}

	@PostMapping("/logout")
	public MessageResponse logout(Authentication authentication) {
		authService.logout((String) authentication.getCredentials());
		return new MessageResponse("Logged out");
	}

	private String generateState() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private ResponseCookie stateCookie(String value, Duration maxAge, HttpServletRequest request) {
		return ResponseCookie.from(STATE_COOKIE, value)
			.httpOnly(true)
			.secure(request.isSecure())
			.sameSite("Lax")
			.path(STATE_COOKIE_PATH)
			.maxAge(maxAge)
			.build();
	}

}
