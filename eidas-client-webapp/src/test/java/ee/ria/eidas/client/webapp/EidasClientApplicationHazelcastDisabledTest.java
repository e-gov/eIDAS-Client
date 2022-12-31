package ee.ria.eidas.client.webapp;

import ee.ria.eidas.client.AuthInitiationService;
import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.AuthnRequestBuilder;
import ee.ria.eidas.client.authnrequest.EidasAttribute;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.fixtures.ResponseBuilder;
import ee.ria.eidas.client.session.UnencodedRequestSession;
import io.restassured.http.ContentType;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.Criterion;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.impl.KeyStoreCredentialResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.junit4.SpringRunner;

import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ee.ria.eidas.client.AuthResponseServiceTest.buildMockHttpServletRequest;
import static io.restassured.RestAssured.given;
import static java.time.Instant.now;
import static java.time.ZoneId.of;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

@RunWith(SpringRunner.class)
@SpringBootTest(
        properties = "spring.main.allow-bean-definition-overriding=true",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EidasClientApplicationHazelcastDisabledTest extends EidasClientApplicationTest {

    @TestConfiguration
    public static class TestConfWithEidasNodeSigningKey {

        @Autowired
        KeyStore samlKeystore;

        @Bean
        public Credential eidasNodeSigningCredential() throws ResolverException {
            Map<String, String> passwordMap = new HashMap<>();
            passwordMap.put("stork", "changeit");
            KeyStoreCredentialResolver resolver = new KeyStoreCredentialResolver(samlKeystore, passwordMap);

            Criterion criterion = new EntityIdCriterion("stork");
            CriteriaSet criteriaSet = new CriteriaSet();
            criteriaSet.add(criterion);

            return resolver.resolveSingle(criteriaSet);
        }
    }

    @Test
    public void heartbeat_shouldSucceed() {
        given()
                .port(port)
                .when()
                .get("/heartbeat")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", notNullValue())
                .body("version", notNullValue())
                .body("buildTime", notNullValue())
                .body("startTime", notNullValue())
                .body("currentTime", notNullValue())
                .body("status", equalTo("UP"))
                .body("dependencies", hasSize(2))
                .body("dependencies[0].status", equalTo("UP"))
                .body("dependencies[0].name", equalTo("credentials"))
                .body("dependencies[1].status", equalTo("UP"))
                .body("dependencies[1].name", equalTo("eIDAS-Node"));
    }

    @Test
    public void hazelcast_shouldNotBeAvailableByDefault() {
        given()
                .port(port)
                .when()
                .get("/hazelcast")
                .then()
                .statusCode(404);
    }

    @Test
    public void heartbeat_shouldFail_whenMetadataSigningCertificateExpired() {
        Mockito.doReturn(false).when(hsmProperties).isEnabled();
        X509Certificate x509 = metadataSigningCredential.getEntityCertificate();
        Instant certificateExpiryTime = x509.getNotAfter().toInstant().plus(1, SECONDS);
        Mockito.doReturn(Clock.fixed(certificateExpiryTime, of("UTC"))).when(credentialsHealthIndicator).getSystemClock();
        assertHeartbeat("DOWN");
    }

    @Test
    public void heartbeat_shouldFail_whenMetadataSigningCertificateNotActive() {
        Mockito.doReturn(false).when(hsmProperties).isEnabled();
        X509Certificate x509 = metadataSigningCredential.getEntityCertificate();
        Instant certificateExpiryTime = x509.getNotBefore().toInstant().minus(1, SECONDS);
        Mockito.doReturn(Clock.fixed(certificateExpiryTime, of("UTC"))).when(credentialsHealthIndicator).getSystemClock();
        assertHeartbeat("DOWN");
    }

    @Test
    public void heartbeat_shouldFail_whenAuthnReqSigningCertificateExpired() {
        Mockito.doReturn(false).when(hsmProperties).isEnabled();
        X509Certificate x509 = authnReqSigningCredential.getEntityCertificate();
        Instant certificateExpiryTime = x509.getNotAfter().toInstant().plus(1, SECONDS);
        Mockito.doReturn(Clock.fixed(certificateExpiryTime, of("UTC"))).when(credentialsHealthIndicator).getSystemClock();
        assertHeartbeat("DOWN");
    }

    @Test
    public void heartbeat_shouldFail_whenAuthnReqSigningCertificateNotActive() {
        Mockito.doReturn(false).when(hsmProperties).isEnabled();
        X509Certificate x509 = authnReqSigningCredential.getEntityCertificate();
        Instant certificateExpiryTime = x509.getNotBefore().toInstant().minus(1, SECONDS);
        Mockito.doReturn(Clock.fixed(certificateExpiryTime, of("UTC"))).when(credentialsHealthIndicator).getSystemClock();
        assertHeartbeat("DOWN");
    }

    @Test
    public void heartbeat_shouldFail_whenResponseAssertionDecryptionCertificateExpired() {
        Mockito.doReturn(false).when(hsmProperties).isEnabled();
        X509Certificate x509 = responseAssertionDecryptionCredential.getEntityCertificate();
        Instant certificateExpiryTime = x509.getNotAfter().toInstant().plus(1, SECONDS);
        Mockito.doReturn(Clock.fixed(certificateExpiryTime, of("UTC"))).when(credentialsHealthIndicator).getSystemClock();
        assertHeartbeat("DOWN");
    }

    @Test
    public void heartbeat_shouldFail_whenResponseAssertionDecryptionCertificateNotActive() {
        Mockito.doReturn(false).when(hsmProperties).isEnabled();
        X509Certificate x509 = responseAssertionDecryptionCredential.getEntityCertificate();
        Instant certificateExpiryTime = x509.getNotBefore().toInstant().minus(1, SECONDS);
        Mockito.doReturn(Clock.fixed(certificateExpiryTime, of("UTC"))).when(credentialsHealthIndicator).getSystemClock();
        assertHeartbeat("DOWN");
    }

    @Test
    public void heartbeat_shouldSucceed_whenSigningWithMetadataSigningCredentialRecovers() {
        Mockito.doReturn(true).when(hsmProperties).isEnabled();
        Mockito.doAnswer(invocation -> {
            throw new InvalidKeyException("Invalid key");
        }).when(metadataSigningCredential).getPrivateKey();

        try {
            spMetadataGenerator.getMetadata();
        } catch (EidasClientException ex) {
            assertEquals("Error generating metadata", ex.getMessage());
        }
        assertHeartbeat("DOWN");
        Mockito.reset(metadataSigningCredential);
        assertHeartbeat("UP");
    }

    @Test
    public void heartbeat_shouldSucceed_whenSigningWithAuthnSigningCredentialRecovers() {
        Mockito.doReturn(true).when(hsmProperties).isEnabled();
        Mockito.doAnswer(invocation -> {
            throw new InvalidKeyException("Invalid key");
        }).when(authnReqSigningCredential).getPrivateKey();

        List<EidasAttribute> requestEidasAttributes = Arrays.asList(EidasAttribute.CURRENT_GIVEN_NAME, EidasAttribute.CURRENT_FAMILY_NAME, EidasAttribute.GENDER);
        AuthnRequestBuilder requestBuilder = new AuthnRequestBuilder(authnReqSigningCredential, eidasClientProperties, idpMetadataResolver.getSingeSignOnService(), applicationEventPublisher);
        try {
            requestBuilder.buildAuthnRequest(AssuranceLevel.SUBSTANTIAL, requestEidasAttributes, SP_TYPE_VALUE, REQUESTER_ID_VALUE,"country");
        } catch (EidasClientException ex) {
            assertEquals("Failed to create authnRequest: Invalid key", ex.getMessage());
        }
        assertHeartbeat("DOWN");
        Mockito.reset(authnReqSigningCredential);
        assertHeartbeat("UP");
    }

    @Test
    public void heartbeat_shouldSucceed_whenDecryptingWithResponseAssertionCredentialRecovers() throws Exception {
        Mockito.doReturn(true).when(hsmProperties).isEnabled();
        Mockito.doAnswer(invocation -> {
            throw new InvalidKeyException("Invalid key");
        }).when(responseAssertionDecryptionCredential).getPrivateKey();

        ResponseBuilder mockResponseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        MockHttpServletRequest mockAuthnRequest = buildMockHttpServletRequest("SAMLResponse", mockResponseBuilder.buildResponse("classpath:idp-metadata.xml"));
        UnencodedRequestSession requestSession = new UnencodedRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO, new DateTime(), AssuranceLevel.SUBSTANTIAL, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);
        requestSessionService.saveRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO, requestSession);
        try {
            authResponseService.getAuthenticationResult(mockAuthnRequest);
        } catch (EidasClientException ex) {
            assertEquals("Error decrypting assertion", ex.getMessage());
        }
        assertHeartbeat("DOWN");
        Mockito.reset(responseAssertionDecryptionCredential);
        assertHeartbeat("UP");
    }


    @Test
    public void noCredentialTestWhen_HsmDisabled() {
        Mockito.doReturn(false).when(hsmProperties).isEnabled();
        Instant lastHealthCheckTime = now();
        Instant lastHsmTestTime = lastHealthCheckTime.minus(credentialsHealthIndicator.getHsmTestInterval()).minus(1, MILLIS);
        Mockito.doReturn(Clock.fixed(lastHealthCheckTime, of("UTC"))).when(credentialsHealthIndicator).getSystemClock();
        Mockito.doReturn(lastHsmTestTime).when(credentialsHealthIndicator).getLastTestTime();

        assertHeartbeat("UP");
        Mockito.verify(credentialsHealthIndicator, times(1)).isInactiveOrExpiredCertificates();
        Mockito.verify(credentialsHealthIndicator, times(1)).isCredentialsInFailedState();
        Mockito.verify(credentialsHealthIndicator, times(0)).isValidForSigning(any(), any());
        Mockito.verify(credentialsHealthIndicator, times(0)).isValidForDecryption(any());
    }

    @Test
    public void noCredentialTestWhen_HsmEnabled_AndHsmCheckIntervalIsNotDue() {
        Mockito.doReturn(true).when(hsmProperties).isEnabled();
        Instant lastHealthCheckTime = now();
        Instant lastHsmTestTime = lastHealthCheckTime.minus(credentialsHealthIndicator.getHsmTestInterval());
        Mockito.doReturn(Clock.fixed(lastHealthCheckTime, of("UTC"))).when(credentialsHealthIndicator).getSystemClock();
        Mockito.doReturn(lastHsmTestTime).when(credentialsHealthIndicator).getLastTestTime();

        assertHeartbeat("UP");
        Mockito.verify(credentialsHealthIndicator, times(1)).isInactiveOrExpiredCertificates();
        Mockito.verify(credentialsHealthIndicator, times(1)).isCredentialsInFailedState();
        Mockito.verify(credentialsHealthIndicator, times(0)).isValidForSigning(any(), any());
        Mockito.verify(credentialsHealthIndicator, times(0)).isValidForDecryption(any());
    }

    @Test
    public void credentialTestWhen_HsmEnabled_AndHsmCheckIntervalIsDue() {
        Mockito.doReturn(true).when(hsmProperties).isEnabled();
        Instant lastHealthCheckTime = now();
        Instant lastHsmTestTime = lastHealthCheckTime.minus(credentialsHealthIndicator.getHsmTestInterval()).minus(1, MILLIS);
        Mockito.doReturn(Clock.fixed(lastHealthCheckTime, of("UTC"))).when(credentialsHealthIndicator).getSystemClock();
        Mockito.doReturn(lastHsmTestTime).when(credentialsHealthIndicator).getLastTestTime();

        assertHeartbeat("UP");
        Mockito.verify(credentialsHealthIndicator, times(1)).isInactiveOrExpiredCertificates();
        Mockito.verify(credentialsHealthIndicator, times(1)).isCredentialsInFailedState();
        Mockito.verify(credentialsHealthIndicator, times(1)).isValidForSigning(any(), eq(metadataSigningCredential));
        Mockito.verify(credentialsHealthIndicator, times(1)).isValidForSigning(any(), eq(authnReqSigningCredential));
        Mockito.verify(credentialsHealthIndicator, times(1)).isValidForDecryption(eq(responseAssertionDecryptionCredential));
    }

    private void assertHeartbeat(String credentialsIndicatorStatus) {
        given()
                .port(port)
                .when()
                .get("/heartbeat")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", notNullValue())
                .body("version", notNullValue())
                .body("buildTime", notNullValue())
                .body("startTime", notNullValue())
                .body("currentTime", notNullValue())
                .body("status", equalTo(credentialsIndicatorStatus))
                .body("dependencies", hasSize(2))
                .body("dependencies[0].status", equalTo(credentialsIndicatorStatus))
                .body("dependencies[0].name", equalTo("credentials"))
                .body("dependencies[1].status", equalTo("UP"))
                .body("dependencies[1].name", equalTo("eIDAS-Node"));
    }
}
