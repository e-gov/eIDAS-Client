package ee.ria.eidas.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.EidasAttribute;
import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.config.EidasCredentialsConfiguration;
import ee.ria.eidas.client.exception.AuthenticationFailedException;
import ee.ria.eidas.client.exception.InvalidRequestException;
import ee.ria.eidas.client.fixtures.ResponseBuilder;
import ee.ria.eidas.client.metadata.IDPMetadataResolver;
import ee.ria.eidas.client.response.AuthenticationResult;
import ee.ria.eidas.client.session.RequestSessionService;
import ee.ria.eidas.client.session.UnencodedRequestSession;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import net.shibboleth.shared.resolver.CriteriaSet;
import net.shibboleth.shared.resolver.Criterion;
import net.shibboleth.shared.resolver.ResolverException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.impl.AttributeStatementBuilder;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.impl.KeyStoreCredentialResolver;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.MissingServletRequestParameterException;

import javax.xml.validation.Schema;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = AuthResponseServiceTest.TestConf.class)
@TestPropertySource(locations = "classpath:application-test.properties")
public class AuthResponseServiceTest {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Autowired
    private RequestSessionService requestSessionService;

    @Autowired
    private EidasClientProperties properties;

    @Autowired
    private IDPMetadataResolver idpMetadataResolver;

    @Autowired
    private Credential responseAssertionDecryptionCredential;

    @Autowired
    private Schema samlSchema;

    @Autowired
    @Qualifier("eidasNodeSigningCredential")
    private Credential eidasNodeSigningCredential;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    private AuthResponseService authResponseService;

    private MockHttpServletRequest httpRequest;

    private ResponseBuilder mockResponseBuilder;

    @Mock
    private Appender mockedAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventCaptor;

    @TestConfiguration
    @Import( { EidasClientConfiguration.class, EidasCredentialsConfiguration.class })
    public static class TestConf {

        @Autowired
        KeyStore softwareKeystore;

        @Bean
        public Credential eidasNodeSigningCredential() throws ResolverException {
            Map<String, String> passwordMap = new HashMap<>();
            passwordMap.put("stork", "changeit");
            KeyStoreCredentialResolver resolver = new KeyStoreCredentialResolver(softwareKeystore, passwordMap);

            Criterion criterion = new EntityIdCriterion("stork");
            CriteriaSet criteriaSet = new CriteriaSet();
            criteriaSet.add(criterion);

            return resolver.resolveSingle(criteriaSet);
        }
    }

    @Before
    public void setUp() {
        authResponseService = new AuthResponseService(requestSessionService, properties, idpMetadataResolver, responseAssertionDecryptionCredential, applicationEventPublisher, samlSchema);
        mockResponseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);

        requestSessionService.getAndRemoveRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO);
        saveNewRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO, Instant.now(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET, "CA");

        Logger root = (Logger) LoggerFactory.getLogger(AuthResponseService.class);
        root.addAppender(mockedAppender);
        root.setLevel(Level.DEBUG);
    }

    @Test
    public void whenResponseStatusSuccessAndValidatedSuccessfully_AuthenticationResultIsReturned() throws Exception {
        httpRequest = buildMockHttpServletRequest("SAMLResponse", mockResponseBuilder.buildResponse("classpath:idp-metadata.xml"));
        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        assertAuthenticationResult(result, AssuranceLevel.LOW.getUri());

        verifyLogs("AuthnResponse ID", Level.INFO);
        verifyLogStartsWithXML("AuthnResponse: ", Level.DEBUG);
        verifyLogs("Decrypted Assertion ID", Level.INFO);
        verifyLogs("AuthnResponse validation: " + StatusCode.SUCCESS, Level.INFO);
    }

    @Test
    public void whenResponseStatusSuccessAndLoaIsValidNotNotified_AuthenticationResultIsReturned() throws Exception {
        httpRequest = buildMockHttpServletRequest("SAMLResponse", mockResponseBuilder.buildResponse("classpath:idp-metadata.xml", "http://eidas.europa.eu/NonNotified/LoA/low"));
        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        assertAuthenticationResult(result, AssuranceLevel.SUBSTANTIAL.getUri());

        verifyLogs("AuthnResponse ID", Level.INFO);
        verifyLogStartsWithXML("AuthnResponse: ", Level.DEBUG);
        verifyLogs("Decrypted Assertion ID", Level.INFO);
        verifyLogs("AuthnResponse validation: " + StatusCode.SUCCESS, Level.INFO);
    }

    @Test
    public void whenResponseStatusSuccessAndLoaIsInvalidNotNotified_thenExceptionIsThrown() throws Exception {
        expectedEx.expect(InvalidRequestException.class);
        expectedEx.expectMessage("Invalid SAMLResponse. AuthnContextClassRef is not greater or equal to the request level of assurance!");

        httpRequest = buildMockHttpServletRequest("SAMLResponse", mockResponseBuilder.buildResponse("classpath:idp-metadata.xml", "http://eidas.europa.eu/NonNotified/LoA/invalid"));

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseLoaLevelIsLowerThanRequested_thenExceptionIsThrown() throws Exception {
        expectedEx.expect(InvalidRequestException.class);
        expectedEx.expectMessage("Invalid SAMLResponse. AuthnContextClassRef is not greater or equal to the request level of assurance!");

        requestSessionService.getAndRemoveRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO);
        saveNewRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO, Instant.now(), AssuranceLevel.SUBSTANTIAL, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET, "CA");
        httpRequest = buildMockHttpServletRequest("SAMLResponse", mockResponseBuilder.buildResponse("classpath:idp-metadata.xml"));
        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseIsMissingMandatoryRequestedEidasAttributes() throws Exception {
        expectedEx.expect(InvalidRequestException.class);
        expectedEx.expectMessage("Invalid SAMLResponse. Missing mandatory attributes in the response assertion: [FamilyName, DateOfBirth, PersonIdentifier, LegalPersonIdentifier]");


        List<EidasAttribute> requestedAttributes = new ArrayList<>(AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);
        requestedAttributes.addAll(Arrays.asList(EidasAttribute.LEGAL_NAME, EidasAttribute.LEGAL_PERSON_IDENTIFIER, EidasAttribute.LEI));
        requestSessionService.getAndRemoveRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO);
        saveNewRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO, Instant.now(), AssuranceLevel.LOW, requestedAttributes, "CA");

        AttributeStatement attributeStatement = new AttributeStatementBuilder().buildObject();
        attributeStatement.getAttributes().add(mockResponseBuilder.buildAttribute("FirstName", "http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri", "eidas-natural:CurrentGivenNameType", "Alexander", "Αλέξανδρος"));
        attributeStatement.getAttributes().add(mockResponseBuilder.buildAttribute("LegalName", "http://eidas.europa.eu/attributes/legalperson/LegalName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri", "eidas:LegalNameTyp", "Acme Corporation", null));
        Response response = mockResponseBuilder.buildResponse("classpath:idp-metadata.xml", Collections.singletonMap(ResponseBuilder.InputType.ATTRIBUTE_STATEMENT, Optional.of(attributeStatement)));
        httpRequest = buildMockHttpServletRequest("SAMLResponse", response);
        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseDoesNotHaveRequestSession_thenExceptionIsThrown2() throws Exception {
        expectedEx.expect(InvalidRequestException.class);
        expectedEx.expectMessage("Invalid SAMLResponse. Assertion issuer's value is not equal to the configured IDP metadata url!");

        httpRequest = buildMockHttpServletRequest("SAMLResponse", mockResponseBuilder.buildResponse("classpath:some_random.xml"));
        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseDoesNotHaveRequestSession_thenExceptionIsThrown() throws Exception {
        expectedEx.expect(InvalidRequestException.class);
        expectedEx.expectMessage("Invalid SAMLResponse. No corresponding SAML request session found for the given response!");

        requestSessionService.getAndRemoveRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO);
        httpRequest = buildMockHttpServletRequest("SAMLResponse", mockResponseBuilder.buildResponse("classpath:idp-metadata.xml"));
        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseHasInvalidInResponseTo_thenExceptionIsThrown() throws Exception {
        expectedEx.expect(InvalidRequestException.class);
        expectedEx.expectMessage("Invalid SAMLResponse. No corresponding SAML request session found for the given response!");

        httpRequest = buildMockHttpServletRequest("SAMLResponse", mockResponseBuilder.buildResponse("classpath:idp-metadata.xml",
                Collections.singletonMap(ResponseBuilder.InputType.IN_RESPONSE_TO, Optional.of("invalid-response-inResponseTo"))));
        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseAssertionHasInvalidInResponseTo_thenExceptionIsThrown() throws Exception {
        expectedEx.expect(InvalidRequestException.class);
        expectedEx.expectMessage("Invalid SAMLResponse. No corresponding SAML request session found for the given response assertion!");

        httpRequest = buildMockHttpServletRequest("SAMLResponse", mockResponseBuilder.buildResponse("classpath:idp-metadata.xml",
                Collections.singletonMap(ResponseBuilder.InputType.ASSERTION_IN_RESPONSE_TO, Optional.of("invalid-assertion-inResponseTo"))));
        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseIsNotSigned_thenExceptionIsThrown() throws Exception {
        expectedEx.expect(InvalidRequestException.class);
        expectedEx.expectMessage("Invalid SAMLResponse. Response not signed.");

        Response response = mockResponseBuilder.buildResponse("classpath:idp-metadata.xml");
        response.setSignature(null);
        httpRequest = buildMockHttpServletRequest("SAMLResponse" ,response);

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseHasInvalidSignature_thenExceptionIsThrown() throws Exception {
        expectedEx.expect(InvalidRequestException.class);
        expectedEx.expectMessage("Invalid SAMLResponse. Invalid response signature.");

        Response response = mockResponseBuilder.buildResponse("classpath:idp-metadata.xml");
        response.setInResponseTo("new-inResponseTo-to-invalidate-signature");
        httpRequest = buildMockHttpServletRequest("SAMLResponse" ,response);

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseIssueInstantHasExpired_thenExceptionIsThrown() throws Exception {
        expectedEx.expect(InvalidRequestException.class);
        expectedEx.expectMessage("Invalid SAMLResponse. Error handling message: Message was rejected due to issue instant expiration");

        Instant pastTime = Instant.now().minusSeconds(properties.getResponseMessageLifetime()).minusSeconds(properties.getAcceptedClockSkew()).minusSeconds(1);
        Response response = mockResponseBuilder.buildResponse("classpath:idp-metadata.xml", Collections.singletonMap(ResponseBuilder.InputType.ISSUE_INSTANT, Optional.of(pastTime)));
        httpRequest = buildMockHttpServletRequest("SAMLResponse" ,response);

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseIssueInstantIsInTheFuture_thenExceptionIsThrown() throws Exception {
        expectedEx.expect(InvalidRequestException.class);
        expectedEx.expectMessage("Invalid SAMLResponse. Error handling message: Message was rejected because it was issued in the future");

        Instant futureTime = Instant.now().plusSeconds(1)
                .plusSeconds(properties.getResponseMessageLifetime()).plusSeconds(properties.getAcceptedClockSkew());
        Response response = mockResponseBuilder.buildResponse("classpath:idp-metadata.xml", Collections.singletonMap(ResponseBuilder.InputType.ISSUE_INSTANT, Optional.of(futureTime)));
        httpRequest = buildMockHttpServletRequest("SAMLResponse" ,response);

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenAuthnContextIsMissing_thenExceptionIsThrown() throws Exception {
        expectedEx.expect(InvalidRequestException.class);
        expectedEx.expectMessage("Invalid SAMLResponse. AuthnStatement must contain AuthnContext!");

        Response response = mockResponseBuilder.buildResponse("classpath:idp-metadata.xml",
                Collections.singletonMap(ResponseBuilder.InputType.AUTHN_CONTEXT, Optional.empty()));
        httpRequest = buildMockHttpServletRequest("SAMLResponse", response);

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenSubjectConfirmationIsMissing_thenExceptionIsThrown() throws Exception {
        expectedEx.expect(InvalidRequestException.class);
        expectedEx.expectMessage("Invalid SAMLResponse. Assertion subject must contain exactly 1 SubjectConfirmation!");

        Response response = mockResponseBuilder.buildResponse("classpath:idp-metadata.xml",
                Collections.singletonMap(ResponseBuilder.InputType.SUBJECT_CONFIRMATION, Optional.empty()));
        httpRequest = buildMockHttpServletRequest("SAMLResponse", response);

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenSubjectConfirmationDataNotOnOrAfterIsInvalid_thenExceptionIsThrown() throws Exception {
        expectedEx.expect(InvalidRequestException.class);
        expectedEx.expectMessage("Invalid SAMLResponse. Assertion condition NotOnOrAfter is not valid!");

        Response response = mockResponseBuilder.buildResponse("classpath:idp-metadata.xml",
                Collections.singletonMap(ResponseBuilder.InputType.ASSERTION_CONDITIONS_NOT_ON_OR_AFTER, Optional.of(Instant.now().minus(Duration.ofHours(8)))));
        httpRequest = buildMockHttpServletRequest("SAMLResponse", response);

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseDoesNotContainSAMLResponse_thenExceptionIsThrown() throws Exception {
        expectedEx.expect(MissingServletRequestParameterException.class);
        expectedEx.expectMessage("Required request parameter 'SAMLResponse' for method parameter type String is not present");

        httpRequest = buildMockHttpServletRequest("someParam", mockResponseBuilder.buildResponse("classpath:idp-metadata.xml"));
        authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseStatusAuthenticationFailed_UnauthorizedIsReturned() throws Exception {
        expectedEx.expect(AuthenticationFailedException.class);
        expectedEx.expectMessage("Authentication failed.");

        Response response = mockResponseBuilder.buildResponse("classpath:idp-metadata.xml",
                Collections.singletonMap(ResponseBuilder.InputType.STATUS, Optional.of(mockResponseBuilder.buildAuthnFailedStatus())));
        httpRequest = buildMockHttpServletRequest("SAMLResponse", response);

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseStatusRequesterRequestDenied_UnauthorizedIsReturned() throws Exception {
        expectedEx.expect(AuthenticationFailedException.class);
        expectedEx.expectMessage("No user consent received. User denied access.");

        Response response = mockResponseBuilder.buildResponse("classpath:idp-metadata.xml",
                Collections.singletonMap(ResponseBuilder.InputType.STATUS, Optional.of(mockResponseBuilder.buildRequesterRequestDeniedStatus())));
        httpRequest = buildMockHttpServletRequest("SAMLResponse", response);

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseDoesNotMatchSchema() throws Exception {
        expectedEx.expect(InvalidRequestException.class);
        expectedEx.expectMessage("Invalid SAMLResponse. Error handling message: Message is not schema-valid.");

        httpRequest = buildMockHttpServletRequest("<saml2p:Response Destination=\"http://localhost:8889/returnUrl\" ID=\"_cce32a4e19aafb6d8c5d4ab4cc60a27a\" InResponseTo=\"sqajsja\" IssueInstant=\"2018-03-26T14:56:41.033Z\" Version=\"2.0\" xmlns:saml2p=\"urn:oasis:names:tc:SAML:2.0:protocol\"></saml2p:Response>");

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseStatusInvalidLoa_InternalErrorIsReturned() throws Exception {
        expectedEx.expect(AuthenticationFailedException.class);
        expectedEx.expectMessage("202019 - Incorrect Level of Assurance in IdP response");

        Response response = mockResponseBuilder.buildResponse("classpath:idp-metadata.xml",
                Collections.singletonMap(ResponseBuilder.InputType.STATUS, Optional.of(mockResponseBuilder.buildInvalidLoaStatus())));
        httpRequest = buildMockHttpServletRequest("SAMLResponse", response);

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseStatusMissingMandatoryAttribute_InternalErrorIsReturned() throws Exception {
        expectedEx.expect(AuthenticationFailedException.class);
        expectedEx.expectMessage("202010 - Mandatory Attribute not found.");

        Response response = mockResponseBuilder.buildResponse("classpath:idp-metadata.xml",
                Collections.singletonMap(ResponseBuilder.InputType.STATUS, Optional.of(mockResponseBuilder.buildMissingMandatoryAttributeStatus())));
        httpRequest = buildMockHttpServletRequest("SAMLResponse" ,response);

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    private void assertAuthenticationResult(AuthenticationResult result, String loa) {
        assertEquals(loa, result.getLevelOfAssurance());
        assertEquals("Αλέξανδρος", result.getAttributes().get("FirstName"));
        assertEquals("Ωνάσης", result.getAttributes().get("FamilyName"));
        assertEquals("Alexander", result.getAttributesTransliterated().get("FirstName"));
        assertEquals("Onassis", result.getAttributesTransliterated().get("FamilyName"));
        assertEquals("CA/CA/12345", result.getAttributes().get("PersonIdentifier"));
        assertEquals("1965-01-01", result.getAttributes().get("DateOfBirth"));
    }

    private MockHttpServletRequest buildMockHttpServletRequest(String response) throws Exception {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setParameter("SAMLResponse", Base64.getEncoder().encodeToString(response.getBytes()));
        httpRequest.setServerName("localhost");
        httpRequest.setServerPort(8889);
        httpRequest.setRequestURI("/returnUrl");
        return httpRequest;
    }

    public static MockHttpServletRequest buildMockHttpServletRequest(String paramName, Response response) throws Exception {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setParameter(paramName, Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(response).getBytes()));
        httpRequest.setServerName("localhost");
        httpRequest.setServerPort(8889);
        httpRequest.setRequestURI("/returnUrl");
        return httpRequest;
    }

    private void saveNewRequestSession(String requestID, Instant issueIntant, AssuranceLevel loa, List<EidasAttribute> requestedAttributes, String country) {
        UnencodedRequestSession requestSession = new UnencodedRequestSession(requestID, issueIntant, loa, requestedAttributes, country);
        requestSessionService.saveRequestSession(requestID, requestSession);
    }

    private void verifyLogs(String logMessage, Level level) {
        verify(mockedAppender, atLeastOnce()).doAppend(loggingEventCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertTrue(loggingEvents.stream().anyMatch(event ->
            event.getFormattedMessage().contains(logMessage) && event.getLevel() == level
        ));
    }

    private void verifyLogStartsWithXML(String logMessage, Level level) {
        verify(mockedAppender, atLeastOnce()).doAppend(loggingEventCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertTrue(loggingEvents.stream().anyMatch(event ->
                event.getFormattedMessage().startsWith(logMessage + "<") && event.getLevel() == level
        ));
    }


}
