package ee.ria.eidas.client.session;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import org.joda.time.DateTime;

public class RequestSession {

    private DateTime issueInstant;
    private AssuranceLevel loa;

    public RequestSession(DateTime issueInstant, AssuranceLevel loa) {
        this.issueInstant = issueInstant;
        this.loa = loa;
    }

    public DateTime getIssueInstant() {
        return issueInstant;
    }

    public AssuranceLevel getLoa() {
        return loa;
    }
}
