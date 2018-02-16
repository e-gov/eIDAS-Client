package ee.ria.eidas.client.authnrequest;

public enum SPType {
    PRIVATE("private"),
    PUBLIC("public");

    private String value;

    SPType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
