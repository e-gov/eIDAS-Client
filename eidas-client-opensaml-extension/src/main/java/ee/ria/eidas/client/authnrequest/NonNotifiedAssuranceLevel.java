package ee.ria.eidas.client.authnrequest;

import lombok.Data;

@Data
public class NonNotifiedAssuranceLevel {

    String country;
    String nonNotifiedLevel;
    String notifiedLevel;

}
