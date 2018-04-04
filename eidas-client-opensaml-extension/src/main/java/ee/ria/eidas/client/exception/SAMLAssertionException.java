package ee.ria.eidas.client.exception;

public class SAMLAssertionException extends EidasClientException {

    public SAMLAssertionException(String message, Throwable cause) {
        super(message, cause);
    }

    public SAMLAssertionException(String message) {
        super(message);
    }

}
