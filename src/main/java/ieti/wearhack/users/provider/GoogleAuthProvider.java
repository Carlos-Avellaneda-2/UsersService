package ieti.wearhack.users.provider;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;

import ieti.wearhack.users.config.GoogleProperties;
import ieti.wearhack.users.dto.GoogleProfile;
import ieti.wearhack.users.exception.InvalidGoogleTokenException;

@Component
public class GoogleAuthProvider {

	private static final Logger log = LoggerFactory.getLogger(GoogleAuthProvider.class);

	private static final List<String> SCOPES = List.of("openid", "email", "profile");

	private final HttpTransport transport = new NetHttpTransport();

	private final JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

	private final GoogleProperties properties;

	private final GoogleIdTokenVerifier verifier;

	public GoogleAuthProvider(GoogleProperties properties) {
		this.properties = properties;
		// Checks signature, expiration and issuer (accounts.google.com); audience must be our client ID.
		this.verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
			.setAudience(List.of(properties.clientId()))
			.build();
	}

	public boolean verifyGoogleToken(String token) {
		return verify(token) != null;
	}

	public GoogleProfile getGoogleProfile(String token) {
		GoogleIdToken idToken = verify(token);
		if (idToken == null) {
			throw new InvalidGoogleTokenException("Invalid or expired Google ID token");
		}
		GoogleIdToken.Payload payload = idToken.getPayload();
		// Accounts are linked by email, so an unverified address must not be trusted.
		if (payload.getEmail() == null || !Boolean.TRUE.equals(payload.getEmailVerified())) {
			throw new InvalidGoogleTokenException("Google account has no verified email");
		}
		return new GoogleProfile(payload.getSubject(), payload.getEmail(), (String) payload.get("name"),
				(String) payload.get("picture"));
	}

	public String buildAuthorizationUrl(String state) {
		return new GoogleAuthorizationCodeRequestUrl(properties.clientId(), properties.redirectUri(), SCOPES)
			.setState(state)
			.build();
	}

	public String exchangeCodeForIdToken(String code) {
		try {
			GoogleTokenResponse response = new GoogleAuthorizationCodeTokenRequest(transport, jsonFactory,
					properties.clientId(), properties.clientSecret(), code, properties.redirectUri())
				.execute();
			String idToken = response.getIdToken();
			if (idToken == null) {
				throw new InvalidGoogleTokenException("Google did not return an ID token");
			}
			return idToken;
		}
		catch (TokenResponseException ex) {
			throw new InvalidGoogleTokenException("Google rejected the authorization code", ex);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not reach Google token endpoint", ex);
		}
	}

	private GoogleIdToken verify(String token) {
		if (token == null || token.isBlank()) {
			return null;
		}
		try {
			return verifier.verify(token);
		}
		catch (GeneralSecurityException | IOException | IllegalArgumentException ex) {
			log.debug("Google ID token verification failed: {}", ex.getMessage());
			return null;
		}
	}

}
