package ieti.wearhack.users.security;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String email) {
}
