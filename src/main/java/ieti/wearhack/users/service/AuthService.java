package ieti.wearhack.users.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import ieti.wearhack.users.config.JwtProperties;
import ieti.wearhack.users.dto.AuthResponse;
import ieti.wearhack.users.dto.GoogleProfile;
import ieti.wearhack.users.dto.UserResponse;
import ieti.wearhack.users.exception.InvalidGoogleTokenException;
import ieti.wearhack.users.exception.InvalidTokenException;
import ieti.wearhack.users.exception.UserNotFoundException;
import ieti.wearhack.users.model.User;
import ieti.wearhack.users.provider.GoogleAuthProvider;
import ieti.wearhack.users.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class AuthService {

	private static final String EMAIL_CLAIM = "email";

	private final UserService userService;

	private final GoogleAuthProvider googleAuthProvider;

	private final SecretKey signingKey;

	private final long expirationMs;

	// Revoked token id (jti) -> expiration of that token.
	private final Map<String, Instant> blacklistedTokens = new ConcurrentHashMap<>();

	public AuthService(UserService userService, GoogleAuthProvider googleAuthProvider, JwtProperties jwtProperties) {
		this.userService = userService;
		this.googleAuthProvider = googleAuthProvider;
		// Throws WeakKeyException when the secret is shorter than 256 bits.
		this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
		this.expirationMs = jwtProperties.expirationMs();
	}

	public String buildGoogleAuthorizationUrl(String state) {
		return googleAuthProvider.buildAuthorizationUrl(state);
	}

	public AuthResponse authenticateGoogleWithCode(String code) {
		return authenticateGoogle(googleAuthProvider.exchangeCodeForIdToken(code));
	}

	public AuthResponse authenticateGoogle(String idToken) {
		if (!googleAuthProvider.verifyGoogleToken(idToken)) {
			throw new InvalidGoogleTokenException("Invalid or expired Google ID token");
		}
		GoogleProfile profile = googleAuthProvider.getGoogleProfile(idToken);

		User user = userService.findByGoogleId(profile.googleId())
			.or(() -> findByEmail(profile.email()))
			.map(existing -> updateFromProfile(existing, profile))
			.orElseGet(() -> userService.createUser(
					new User(profile.name(), profile.email(), profile.googleId(), profile.picture())));

		return AuthResponse.bearer(generateJwt(user), expirationMs, UserResponse.from(user));
	}

	public boolean validateToken(String token) {
		return parseValidClaims(token).isPresent();
	}

	public AuthenticatedUser getAuthenticatedUser(String token) {
		Claims claims = parseValidClaims(token)
			.orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));
		return new AuthenticatedUser(UUID.fromString(claims.getSubject()), claims.get(EMAIL_CLAIM, String.class));
	}

	public void logout(String token) {
		Claims claims = parseValidClaims(token)
			.orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));
		removeExpiredBlacklistEntries();
		blacklistedTokens.put(claims.getId(), claims.getExpiration().toInstant());
	}

	private User updateFromProfile(User user, GoogleProfile profile) {
		user.setGoogleId(profile.googleId());
		user.setName(profile.name());
		user.setPicture(profile.picture());
		return userService.updateUser(user);
	}

	private Optional<User> findByEmail(String email) {
		try {
			return Optional.of(userService.findByEmail(email));
		}
		catch (UserNotFoundException ex) {
			return Optional.empty();
		}
	}

	private String generateJwt(User user) {
		Instant now = Instant.now();
		return Jwts.builder()
			.id(UUID.randomUUID().toString())
			.subject(user.getId().toString())
			.claim(EMAIL_CLAIM, user.getEmail())
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plusMillis(expirationMs)))
			.signWith(signingKey, Jwts.SIG.HS256)
			.compact();
	}

	private Optional<Claims> parseValidClaims(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		try {
			Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
			if (claims.getId() == null || claims.getSubject() == null || claims.getExpiration() == null
					|| blacklistedTokens.containsKey(claims.getId())) {
				return Optional.empty();
			}
			return Optional.of(claims);
		}
		catch (JwtException | IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	private void removeExpiredBlacklistEntries() {
		Instant now = Instant.now();
		blacklistedTokens.values().removeIf(expiration -> expiration.isBefore(now));
	}

}
