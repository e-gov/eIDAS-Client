package ee.ria.eidas.client.authnrequest;

public enum AssuranceLevel {

    LOW("http://eidas.europa.eu/LoA/low", 1),
    SUBSTANTIAL("http://eidas.europa.eu/LoA/substantial", 2),
    HIGH("http://eidas.europa.eu/LoA/high", 3);

    private String uri;

    private int level;

    AssuranceLevel(String uri, int level) {
        this.uri = uri;
        this.level = level;
    }

    public String getUri() {
        return uri;
    }

    public int getLevel() {
        return level;
    }
}
