package ee.ria.eidas.client.webapp;

public class EidasClientApi {

    public static final String ENDPOINT_METADATA_METADATA = "/metadata";
    public static final String ENDPOINT_METADATA_SUPPORTED_COUNTRIES = "/supportedCountries";

    public static final String ENDPOINT_AUTHENTICATION_LOGIN = "/login";
    public static final String ENDPOINT_AUTHENTICATION_RETURN_URL = "/returnUrl";

    private EidasClientApi() { throw new IllegalStateException("Utility class"); }

    public enum Endpoint {

        METADATA(ENDPOINT_METADATA_METADATA, Type.METADATA),
        SUPPORTED_COUNTRIES(ENDPOINT_METADATA_SUPPORTED_COUNTRIES, Type.METADATA),

        LOGIN(ENDPOINT_AUTHENTICATION_LOGIN, Type.AUTHENTICATION),
        RETURN_URL(ENDPOINT_AUTHENTICATION_RETURN_URL, Type.AUTHENTICATION);

        public enum Type {
            METADATA, AUTHENTICATION;
        }

        public final String urlPattern;
        public final Type type;

        Endpoint(String urlPattern, Type type) {
            this.urlPattern = urlPattern;
            this.type = type;
        }

        public String getUrlPattern() {
            return this.urlPattern;
        }

        public Type getType() {
            return this.type;
        }

    }

}
