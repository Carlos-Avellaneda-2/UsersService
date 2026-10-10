package ieti.wearhack.users.exception;

public class InvalidOAuthStateException extends RuntimeException {

	public InvalidOAuthStateException(String message) {
		super(message);
	}

}
