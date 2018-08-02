package ee.ria.eidas.client.session;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.EidasAttribute;
import org.joda.time.DateTime;

import java.util.List;

public class RequestSession {

    private final String requestId;
    private final DateTime issueInstant;
    private final AssuranceLevel loa;
    private final List<EidasAttribute> requestedAttributes;

    public RequestSession(String requestId, DateTime issueInstant, AssuranceLevel loa, List<EidasAttribute> requestedAttributes) {
        this.requestId = requestId;
        this.issueInstant = issueInstant;
        this.loa = loa;
        this.requestedAttributes = requestedAttributes;
    }

    public String getRequestId() {
        return requestId;
    }

    public DateTime getIssueInstant() {
        return issueInstant;
    }

    public AssuranceLevel getLoa() {
        return loa;
    }

    public List<EidasAttribute> getRequestedAttributes() {
        return requestedAttributes;
    }
}
