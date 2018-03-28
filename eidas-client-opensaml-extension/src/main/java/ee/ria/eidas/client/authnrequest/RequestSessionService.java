package ee.ria.eidas.client.authnrequest;

import ee.ria.eidas.client.RequestSession;

public interface RequestSessionService {

    RequestSession getRequestSession(String requestID);

    void saveRequestSession(String requestID, RequestSession requestSession);

    void removeRequestSession(String requestID);

}
