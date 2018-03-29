package ee.ria.eidas.client.session;

import org.joda.time.DateTime;

public class RequestSession {

    private DateTime issueInstant;

    public RequestSession(DateTime issueInstant) {
        this.issueInstant = issueInstant;
    }

    public DateTime getIssueInstant() {
        return issueInstant;
    }
}
