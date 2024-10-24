package ee.ria.eidas.client.session;


import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.EidasAttribute;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class EncodedRequestSession implements RequestSession {

    public static final String OPERATION_NOT_SUPPORTED = "operation not supported";

    private final String requestId;
    private final byte[] encodedRequestSession;

    @Override
    public int compareTo(final RequestSession o) {
        return getRequestId().compareTo(o.getRequestId());
    }

    @Override
    public Instant getIssueInstant() {
        throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
    }

    @Override
    public AssuranceLevel getLoa() {
        throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
    }

    @Override
    public String getCountry() {
        throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
    }

    @Override
    public List<EidasAttribute> getRequestedAttributes() {
        throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
    }
}
