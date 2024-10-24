package ee.ria.eidas.client.session;

import ee.ria.eidas.client.AuthInitiationService;
import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.config.EidasCredentialsConfiguration;
import ee.ria.eidas.client.exception.EidasClientException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.Instant;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { EidasClientConfiguration.class, EidasCredentialsConfiguration.class })
@TestPropertySource(locations = "classpath:application-test.properties")
public class LocalRequestSessionServiceImplTest {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    LocalRequestSessionServiceImpl requestSessionService;

    @Autowired
    EidasClientProperties properties;

    @Before
    public void setUp() {
        properties.setMaximumAuthenticationLifetime(1);
        requestSessionService = new LocalRequestSessionServiceImpl(properties);
    }

    @Test
    public void getRequestSession_returnsSession_whenSavedBeforehand() {
        String requestID = "_4ededd23fb88e6964df71b8bdb1c706f";
        UnencodedRequestSession requestSession = new UnencodedRequestSession(requestID, Instant.now(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET, "CA");
        requestSessionService.saveRequestSession(requestID, requestSession);
        assertNotNull(requestSessionService.getAndRemoveRequestSession(requestID));
    }

    @Test
    public void getRequestSession_returnsNull_whenNotSavedBeforehand() {
        String requestID = "_4ededd23fb88e6964df71b8bdb1c706f";
        assertNull(requestSessionService.getAndRemoveRequestSession(requestID));
    }

    @Test
    public void saveRequestSession_throwsException_whenSessionWithSameIdAlreadyExists() {
        expectedEx.expect(EidasClientException.class);
        expectedEx.expectMessage("A request with an ID: _4ededd23fb88e6964df71b8bdb1c706f already exists!");

        String requestID = "_4ededd23fb88e6964df71b8bdb1c706f";
        UnencodedRequestSession requestSession = new UnencodedRequestSession(requestID, Instant.now(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET, "CA");
        requestSessionService.saveRequestSession(requestID, requestSession);
        requestSessionService.saveRequestSession(requestID, requestSession);
    }

    @Test
    public void getRequestSession_returnsNull_whenSessionIsRemovedBeforehand() {
        String requestID = "_4ededd23fb88e6964df71b8bdb1c706f";
        UnencodedRequestSession requestSession = new UnencodedRequestSession(requestID, Instant.now(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET, "CA");
        requestSessionService.saveRequestSession(requestID, requestSession);
        requestSessionService.getAndRemoveRequestSession(requestID);

        assertNull(requestSessionService.getAndRemoveRequestSession(requestID));
    }

    @Test
    public void getRequestSession_returnsNull_whenSessionExpiresAndThereforeIsRemovedBeforehand() throws InterruptedException {
        String requestID = "_4ededd23fb88e6964df71b8bdb1c706f";
        Instant timeInPast = Instant.now().minusSeconds(properties.getMaximumAuthenticationLifetime()).minusSeconds(properties.getAcceptedClockSkew()).minusSeconds(1);
        UnencodedRequestSession requestSession = new UnencodedRequestSession(requestID, timeInPast, AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET, "CA");
        requestSessionService.saveRequestSession(requestID, requestSession);
        requestSessionService.removeExpiredSessions();
        assertNull(requestSessionService.getAndRemoveRequestSession(requestID));
    }

}
