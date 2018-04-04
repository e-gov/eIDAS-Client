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

    public static AssuranceLevel toEnum(String uri) {
        for(AssuranceLevel v : values())
            if(v.getUri().equalsIgnoreCase(uri)) return v;
        throw new IllegalArgumentException("Cannot convert uri to enum value! Expected one of: " + values());
    }
}
