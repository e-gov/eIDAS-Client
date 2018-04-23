package ee.ria.eidas.client.session;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.EidasAttribute;
import org.joda.time.DateTime;

import java.util.List;

public class RequestSession {

    private DateTime issueInstant;
    private AssuranceLevel loa;
    private List<EidasAttribute> requestedAttributes;

    public RequestSession(DateTime issueInstant, AssuranceLevel loa, List<EidasAttribute> requestedAttributes) {
        this.issueInstant = issueInstant;
        this.loa = loa;
        this.requestedAttributes = requestedAttributes;
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
