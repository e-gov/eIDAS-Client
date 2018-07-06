package ee.ria.eidas.client.session;

public interface RequestSessionService {

    void saveRequestSession(String requestID, RequestSession requestSession);

    RequestSession getAndRemoveRequestSession(String requestID);

}
