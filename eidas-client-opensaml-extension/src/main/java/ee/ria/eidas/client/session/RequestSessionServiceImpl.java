package ee.ria.eidas.client.session;

import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.EidasClientException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashMap;
import java.util.Map;

public class RequestSessionServiceImpl implements RequestSessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestSessionServiceImpl.class);

    private final Object requestSessionLock = new Object();
    private final Map<String, RequestSession> requestSessionMap = new HashMap<String, RequestSession>();

    private int maxAuthenticationLifetime;

    private int accepterClockSkew;

    public RequestSessionServiceImpl(EidasClientProperties properties) {
        this.maxAuthenticationLifetime = properties.getMaximumAuthenticationLifetime();
        this.accepterClockSkew = properties.getAcceptedClockSkew();
    }

    @Override
    public void saveRequestSession(String requestID, RequestSession requestSession) {
        synchronized (requestSessionLock) {
            if (requestSessionMap.containsKey(requestID)) {
                throw new EidasClientException("A request with an ID: " + requestID + " already exists!");
            }
            requestSessionMap.put(requestID, requestSession);
        }
    }

    @Override
    public RequestSession getAndRemoveRequestSession(String requestID) {
        synchronized (requestSessionLock) {
            return requestSessionMap.remove(requestID);
        }
    }

    @Scheduled(cron = "0 * * * * *")
    public void removeExpiredSessions() {
        synchronized (requestSessionLock) {
            LOGGER.info("Triggering removal of expired SAML request sessions");
            requestSessionMap.entrySet().removeIf(
                    requestSession -> {
                        DateTime now = new DateTime(requestSession.getValue().getIssueInstant().getZone());
                        boolean expired = now.isAfter(requestSession.getValue().getIssueInstant().plusSeconds(maxAuthenticationLifetime).plusSeconds(accepterClockSkew));
                        if (expired) {
                            LOGGER.info("Removing expired request session with ID: " + requestSession.getKey());
                        }
                        return expired;
                    }
            );
        }
    }


}
