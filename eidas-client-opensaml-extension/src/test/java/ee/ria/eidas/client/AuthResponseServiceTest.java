package ee.ria.eidas.client;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.exception.EidasAuthenticationFailedException;
import ee.ria.eidas.client.fixtures.ResponseBuilder;
import ee.ria.eidas.client.response.AuthenticationResult;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.Criterion;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.saml2.core.*;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.impl.KeyStoreCredentialResolver;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.security.KeyStore;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = AuthResponseServiceTest.TestConf.class)
@TestPropertySource(locations = "classpath:application-test.properties")
public class AuthResponseServiceTest {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Autowired
    private EidasClientProperties properties;

    @Autowired
    private ExplicitKeySignatureTrustEngine responseSignatureTrustEngine;

    @Autowired
    private Credential responseAssertionDecryptionCredential;

    @Autowired
    @Qualifier("eidasNodeSigningCredential")
    private Credential eidasNodeSigningCredential;

    AuthResponseService authResponseService;

    private MockHttpServletRequest httpRequest;

    private ResponseBuilder mockResponseBuilder;

    @TestConfiguration
    @Import(EidasClientConfiguration.class)
    public static class TestConf {

        @Autowired
        KeyStore samlKeystore;

        @Bean
        @ConditionalOnProperty(name="test.property", havingValue="A")
        public ExplicitKeySignatureTrustEngine idpMetadataSignatureTrustEngine(KeyStore keyStore) {
            throw new RuntimeException("Something went horribly wrong..");
        }

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

    @Before
    public void setUp() throws Exception {
        authResponseService = new AuthResponseService(properties, responseSignatureTrustEngine, responseAssertionDecryptionCredential);
        mockResponseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        httpRequest = buildMockHttpServletRequest(mockResponseBuilder.buildResponse());
    }

    @Test
    public void whenResponseStatusSuccessAndValidatedSuccessfully_AuthenticationResultIsReturned() {
        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        assertAuthenticationResult(result);
    }

    @Test
    public void whenResponseStatusAuthenticationFailed_UnauthorizedIsReturned() throws Exception {

        expectedEx.expect(EidasAuthenticationFailedException.class);
        expectedEx.expectMessage("Authentication failed.");

        Response response = mockResponseBuilder.buildResponse();
        response.setStatus(mockResponseBuilder.buildAuthnFailedStatus());
        httpRequest = buildMockHttpServletRequest(response);

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseStatusRequesterRequestDenied_UnauthorizedIsReturned() throws Exception {

        expectedEx.expect(EidasAuthenticationFailedException.class);
        expectedEx.expectMessage("No user consent received. User denied access.");

        Response response = mockResponseBuilder.buildResponse();
        response.setStatus(mockResponseBuilder.buildRequesterRequestDeniedStatus());
        httpRequest = buildMockHttpServletRequest(response);

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseStatusInvalidLoa_InternalErrorIsReturned() throws Exception {

        expectedEx.expect(EidasClientException.class);
        expectedEx.expectMessage("Eidas node responded with an error! statusCode = urn:oasis:names:tc:SAML:2.0:status:Responder, statusMessage = 202019 - Incorrect Level of Assurance in IdP response");

        Response response = mockResponseBuilder.buildResponse();
        response.setStatus(mockResponseBuilder.buildInvalidLoaStatus());
        httpRequest = buildMockHttpServletRequest(response);

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    @Test
    public void whenResponseStatusMissingMandatoryAttribute_InternalErrorIsReturned() throws Exception {

        expectedEx.expect(EidasClientException.class);
        expectedEx.expectMessage("Eidas node responded with an error! statusCode = urn:oasis:names:tc:SAML:2.0:status:Responder, statusMessage = 202010 - Mandatory Attribute not found.");

        Response response = mockResponseBuilder.buildResponse();
        response.setStatus(mockResponseBuilder.buildMissingMandatoryAttributeStatus());
        httpRequest = buildMockHttpServletRequest(response);

        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        fail("Should not reach this!");
    }

    private void assertAuthenticationResult(AuthenticationResult result) {
        assertEquals(AssuranceLevel.LOW.getUri(), result.getLevelOfAssurance());
        assertEquals("Alexander", result.getAttributes().get("FirstName"));
        assertEquals("Onassis", result.getAttributes().get("FamilyName"));
        assertEquals("Αλέξανδρος", result.getAttributesNonLatin().get("FirstName"));
        assertEquals("Ωνάσης", result.getAttributesNonLatin().get("FamilyName"));
        assertEquals("CA/CA/12345", result.getAttributes().get("PersonIdentifier"));
        assertEquals("1965-01-01", result.getAttributes().get("DateOfBirth"));
    }

    private MockHttpServletRequest buildMockHttpServletRequest(Response response) throws Exception {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setParameter("SAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(response).getBytes()));
        httpRequest.setServerName("localhost");
        httpRequest.setServerPort(8889);
        httpRequest.setRequestURI("/returnUrl");
        return httpRequest;
    }

}