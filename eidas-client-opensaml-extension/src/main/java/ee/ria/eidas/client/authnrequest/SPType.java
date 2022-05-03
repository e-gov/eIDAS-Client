package ee.ria.eidas.client.authnrequest;

import com.fasterxml.jackson.annotation.JsonValue;

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

    @JsonValue
    public String toLowerCase() {
        return toString().toLowerCase();
    }
}
