package ee.ria.eidas.client.session;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.EidasAttribute;
import lombok.Data;
import org.joda.time.DateTime;

import java.util.List;

@Data
public class UnencodedRequestSession implements RequestSession {
    private final String requestId;
    private final DateTime issueInstant;
    private final AssuranceLevel loa;
    private final List<EidasAttribute> requestedAttributes;

    @Override
    public int compareTo(final RequestSession o) {
        return getRequestId().compareTo(o.getRequestId());
    }
}

@Data
class EncodedRequestSession implements RequestSession {

    public static final String OPERATION_NOT_SUPPORTED = "operation not supported";
    private final String requestId;
    private final byte[] encodedRequestSession;

    @Override
    public int compareTo(final RequestSession o) {
        return getRequestId().compareTo(o.getRequestId());
    }

    @Override
    public DateTime getIssueInstant() {
        throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
    }

    @Override
    public AssuranceLevel getLoa() {
        throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
    }

    @Override
    public List<EidasAttribute> getRequestedAttributes() {
        throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
    }
}