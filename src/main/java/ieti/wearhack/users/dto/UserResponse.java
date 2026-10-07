package ieti.wearhack.users.dto;

import java.time.Instant;
import java.util.UUID;

import ieti.wearhack.users.model.User;

public record UserResponse(UUID id, String name, String email, String picture, Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPicture(),
				user.getCreatedAt());
	}

}
