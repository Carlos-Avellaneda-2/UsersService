package ieti.wearhack.users.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(@NotBlank(message = "idToken is required") String idToken) {
}
