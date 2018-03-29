package ee.ria.eidas.client.session;

public interface RequestSessionService {

    RequestSession getRequestSession(String requestID);

    void saveRequestSession(String requestID, RequestSession requestSession);

    void removeRequestSession(String requestID);

}
