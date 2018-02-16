package ee.ria.eidas.client.authnrequest;

public enum AssuranceLevel {

    LOW("http://eidas.europa.eu/LoA/low"),
    SUBSTANTIAL("http://eidas.europa.eu/LoA/substantial"),
    HIGH("http://eidas.europa.eu/LoA/high");

    private String uri;

    AssuranceLevel(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }
}
