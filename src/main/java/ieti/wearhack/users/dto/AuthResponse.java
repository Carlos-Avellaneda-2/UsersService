package ieti.wearhack.users.dto;

public record AuthResponse(String token, String tokenType, long expiresInMs, UserResponse user) {

	public static AuthResponse bearer(String token, long expiresInMs, UserResponse user) {
		return new AuthResponse(token, "Bearer", expiresInMs, user);
	}

}
