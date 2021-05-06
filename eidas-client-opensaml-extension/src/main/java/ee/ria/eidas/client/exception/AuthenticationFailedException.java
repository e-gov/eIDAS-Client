package ee.ria.eidas.client.exception;

import lombok.Getter;

public class AuthenticationFailedException extends RuntimeException {

    @Getter
    private final String status;
    @Getter
    private String subStatus;

    public AuthenticationFailedException(String message, String status, String subStatus) {
        super(message);
        this.status = status;
        this.subStatus = subStatus;
    }

    public AuthenticationFailedException(String message, String status) {
        super(message);
        this.status = status;
    }
}
