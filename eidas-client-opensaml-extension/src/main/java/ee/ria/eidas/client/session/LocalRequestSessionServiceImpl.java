package ee.ria.eidas.client.session;

import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.EidasClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class LocalRequestSessionServiceImpl implements RequestSessionService {

    private final Object requestSessionLock = new Object();

    private final Map<String, RequestSession> requestSessionMap = new HashMap<String, RequestSession>();

    private final int maxAuthenticationLifetime;

    private final int acceptedClockSkew;

    public LocalRequestSessionServiceImpl(EidasClientProperties properties) {
        log.info("Using in memory map for request tracking");
        this.maxAuthenticationLifetime = properties.getMaximumAuthenticationLifetime();
        this.acceptedClockSkew = properties.getAcceptedClockSkew();
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
            log.info("Triggering removal of expired SAML request sessions");
            requestSessionMap.entrySet().removeIf(
                    requestSession -> {
                        Instant now = Instant.now();
                        boolean expired = now.isAfter(requestSession.getValue().getIssueInstant().plusSeconds(maxAuthenticationLifetime).plusSeconds(acceptedClockSkew));
                        if (expired) {
                            log.info("Removing expired request session with ID: " + requestSession.getKey());
                        }
                        return expired;
                    }
            );
        }
    }
}
