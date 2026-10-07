package ieti.wearhack.users.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "google")
public record GoogleProperties(@NotBlank String clientId, @NotBlank String clientSecret,
		@NotBlank String redirectUri) {
}
