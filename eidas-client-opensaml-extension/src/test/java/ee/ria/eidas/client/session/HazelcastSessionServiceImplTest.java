package ee.ria.eidas.client.session;

import com.google.common.io.ByteSource;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import ee.ria.eidas.client.AuthInitiationService;
import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.config.HazelcastConfiguration;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.util.SerializationUtils;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.UUID;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { EidasClientConfiguration.class, HazelcastConfiguration.class})
@TestPropertySource(locations = "classpath:application-test-hazelcast-enabled.properties")
@Slf4j
public class HazelcastSessionServiceImplTest {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Autowired
    RequestSessionService requestSessionService;

    @Autowired
    EidasClientProperties properties;

    @Autowired
    HazelcastInstance hazelcastInstance;

    @Before
    public void setUp() {
    }

    @Test
    public void getRequestSession_returnsSession_whenSavedBeforehand() {
        String requestID = UUID.randomUUID().toString();
        UnencodedRequestSession originalRequestSession = new UnencodedRequestSession(requestID, new DateTime(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);
        requestSessionService.saveRequestSession(requestID, originalRequestSession);

        // verify stored requestSession is serialized encrypted properly in Hazelcast
        IMap<String, RequestSession> map = getRequestSessionMapInstance();
        RequestSession session = map.get(HazelcastRequestSessionServiceImpl.sha512(originalRequestSession.getRequestId()));
        Assert.assertNotNull("Session not found by requestId hash",session);
        Assert.assertTrue("Session stored in map must be encoded!", session instanceof EncodedRequestSession);

        // verify returned requestSession is deserialised and decrypted properly
        RequestSession fetchedRequestSession = requestSessionService.getAndRemoveRequestSession(requestID);

        Assert.assertTrue("Fetched session must be deserialised and decrypted to RequestSession!", originalRequestSession instanceof UnencodedRequestSession);
        assertEquals(originalRequestSession.getRequestId(), fetchedRequestSession.getRequestId());
        assertEquals(originalRequestSession.getIssueInstant(), fetchedRequestSession.getIssueInstant());
        assertEquals(originalRequestSession.getLoa(), fetchedRequestSession.getLoa());
        assertEquals(originalRequestSession.getRequestedAttributes(), fetchedRequestSession.getRequestedAttributes());
    }

    @Test
    public void getRequestSession_returnsTamperedSessionInvalidSerializedClass() {
        String requestID = UUID.randomUUID().toString();
        UnencodedRequestSession originalRequestSession = new UnencodedRequestSession(requestID, new DateTime(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);
        requestSessionService.saveRequestSession(requestID, originalRequestSession);

        IMap<String, String> map = this.hazelcastInstance.getMap(HazelcastRequestSessionServiceImpl.UNANSWERED_REQUESTS_MAP);
        map.put(HazelcastRequestSessionServiceImpl.sha512(originalRequestSession.getRequestId()), "....");


        expectedEx.expect(ClassCastException.class);
        expectedEx.expectMessage("java.lang.String cannot be cast to ee.ria.eidas.client.session.RequestSession");
        requestSessionService.getAndRemoveRequestSession(requestID);
    }

    @Test
    public void getRequestSession_returnsTamperedSessionInvalidSignature() throws Exception {
        String requestID = UUID.randomUUID().toString();
        UnencodedRequestSession originalRequestSession = new UnencodedRequestSession(requestID, new DateTime(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);
        requestSessionService.saveRequestSession(requestID, originalRequestSession);

        IMap<String, RequestSession> map = this.hazelcastInstance.getMap(HazelcastRequestSessionServiceImpl.UNANSWERED_REQUESTS_MAP);

        byte[] encodedObject = SerializationUtils.serializeAndEncodeObject( new HazelcastRequestSessionServiceImpl.DefaultCipherExecutor("C5N8eS_6iCo0ib9L", "TWXUmJHr8O9yxZmX1VS4xpZSg2U3bZQ7mCWVoZCKQAipbv1MbFF_xDkhQrfsG5Abh5o2xqTFTLSvYeUx9BfU5A", "AES", "HS512"), new UnencodedRequestSession(requestID, new DateTime(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET));
        RequestSession newSession = new EncodedRequestSession(HazelcastRequestSessionServiceImpl.sha512(originalRequestSession.getRequestId()), ByteSource.wrap(encodedObject).read());
        map.put(HazelcastRequestSessionServiceImpl.sha512(originalRequestSession.getRequestId()), newSession);

        expectedEx.expect(IllegalStateException.class);
        expectedEx.expectMessage("Invalid signature detected!");
        requestSessionService.getAndRemoveRequestSession(requestID);
    }


    @Test
    public void getRequestSession_returnsNull_whenNotSavedBeforehand() {
        String requestID = UUID.randomUUID().toString();
        assertNull("expected that no entry is found for key: " + requestID, requestSessionService.getAndRemoveRequestSession(requestID));
    }

    @Test
    public void getRequestSessionThrowsExceptionWhenNoID() {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("requestID cannot be empty!");
        requestSessionService.getAndRemoveRequestSession("  ");
    }

    @Test
    public void saveRequestSession_throwsException_whenSessionWithSameIdAlreadyExists() {
        expectedEx.expect(EidasClientException.class);
        expectedEx.expectMessage("A request with an ID: _4ededd23fb88e6964df71b8bdb1c706f already exists!");

        String requestID = "_4ededd23fb88e6964df71b8bdb1c706f";
        UnencodedRequestSession requestSession = new UnencodedRequestSession(requestID, new DateTime(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);
        requestSessionService.saveRequestSession(requestID, requestSession);
        requestSessionService.saveRequestSession(requestID, requestSession);
    }

    @Test
    public void getRequestSession_returnsNull_whenSessionIsRemovedBeforehand() {
        String requestID = "_4ededd23fb88e6964df71b8bdb1c706f";
        UnencodedRequestSession requestSession = new UnencodedRequestSession(requestID, new DateTime(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);
        requestSessionService.saveRequestSession(requestID, requestSession);
        requestSessionService.getAndRemoveRequestSession(requestID);

        assertNull(requestSessionService.getAndRemoveRequestSession(requestID));
    }

    private IMap<String, RequestSession> getRequestSessionMapInstance() {
        try {
            IMap<String, RequestSession> inst = this.hazelcastInstance.getMap(HazelcastRequestSessionServiceImpl.UNANSWERED_REQUESTS_MAP);
            return inst;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

}
