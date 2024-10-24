package ee.ria.eidas.client.session;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.EidasAttribute;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class UnencodedRequestSession implements RequestSession {
    private final String requestId;
    private final Instant issueInstant;
    private final AssuranceLevel loa;
    private final List<EidasAttribute> requestedAttributes;
    private final String country;

    @Override
    public int compareTo(final RequestSession o) {
        return getRequestId().compareTo(o.getRequestId());
    }
}
