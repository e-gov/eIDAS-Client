package ee.ria.eidas.client.session;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.EidasAttribute;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public interface RequestSession extends Serializable, Comparable<RequestSession> {
    String getRequestId();

    Instant getIssueInstant();

    AssuranceLevel getLoa();

    List<EidasAttribute> getRequestedAttributes();

    String getCountry();
}
