package ee.ria.eidas.client.session;

import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.EidasClientException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RequestSessionServiceImpl implements RequestSessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestSessionServiceImpl.class);

    private Map<String, RequestSession> requestSessionMap = Collections.synchronizedMap(new HashMap<String, RequestSession>());

    private int maxAuthenticationLifetime;

    public RequestSessionServiceImpl(EidasClientProperties properties) {
        this.maxAuthenticationLifetime = properties.getMaximumAuthenticationLifetime();
    }

    @Override
    public RequestSession getRequestSession(String requestID) {
        return requestSessionMap.get(requestID);
    }

    @Override
    public void saveRequestSession(String requestID, RequestSession requestSession) {
        if (requestSessionMap.containsKey(requestID)) {
            throw new EidasClientException("A request with an ID: " + requestID + " already exists!");
        }
        requestSessionMap.put(requestID, requestSession);
    }

    @Override
    public void removeRequestSession(String requestID) {
        requestSessionMap.remove(requestID);
    }

    @Scheduled(cron = "0 * * * * *")
    public void removeExpiredSessions() {
        LOGGER.info("Triggering removal of expired SAML request sessions");
        requestSessionMap.entrySet().removeIf(
                requestSession -> {
                    DateTime now = new DateTime(requestSession.getValue().getIssueInstant().getZone());
                    boolean expired = now.isAfter(requestSession.getValue().getIssueInstant().plusSeconds(maxAuthenticationLifetime));
                    if (expired) {
                        LOGGER.info("Removing expired request session with ID: " + requestSession.getKey());
                    }
                    return expired;
                }
        );
    }


}
