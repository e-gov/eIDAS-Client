package ee.ria.eidas.client.exception;

public class EidasAuthenticationFailedException extends RuntimeException {

    public EidasAuthenticationFailedException(String message) {
        super(message);
    }
}
