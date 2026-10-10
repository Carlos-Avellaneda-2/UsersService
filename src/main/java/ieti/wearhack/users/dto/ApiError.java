package ieti.wearhack.users.dto;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public record ApiError(Instant timestamp, int status, String error, String message, String path) {

	public static ApiError of(HttpStatusCode status, String message, String path) {
		HttpStatus resolved = HttpStatus.resolve(status.value());
		String error = resolved != null ? resolved.getReasonPhrase() : "Error";
		return new ApiError(Instant.now(), status.value(), error, message, path);
	}

}
