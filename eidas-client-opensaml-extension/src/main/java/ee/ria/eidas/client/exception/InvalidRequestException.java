package ee.ria.eidas.client.exception;

public class InvalidRequestException extends EidasClientException {

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidRequestException(String message) {
        super(message);
    }
}
