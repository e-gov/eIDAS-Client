package ee.ria.eidas.client.exception;

public class InvalidEidasParamException extends EidasClientException {

    public InvalidEidasParamException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidEidasParamException(String message) {
        super(message);
    }
}
