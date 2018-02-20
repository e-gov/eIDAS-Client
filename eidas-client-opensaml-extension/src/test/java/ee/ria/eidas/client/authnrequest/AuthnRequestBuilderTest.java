package ee.ria.eidas.client.authnrequest;

import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.metadata.IDPMetadataResolver;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.*;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.credential.Credential;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = EidasClientConfiguration.class)
@TestPropertySource(locations="classpath:application-test.properties")
public class AuthnRequestBuilderTest {

    @Autowired
    private EidasClientProperties properties;

    @Autowired
    private Credential authnReqSigningCredential;

    @Autowired
    private SingleSignOnService singleSignOnService;

    private AuthnRequestBuilder requestBuilder;

    @Before
    public void setUp() {
        requestBuilder = new AuthnRequestBuilder(authnReqSigningCredential, properties, singleSignOnService);
    }

    @Test
    public void buildAuthnRequest() {
        AuthnRequest authnRequest = requestBuilder.buildAuthnRequest(AssuranceLevel.LOW);
        assertAuthnRequest(authnRequest);
    }

    private void assertAuthnRequest(AuthnRequest authnRequest) {
        assertTrue(authnRequest.isForceAuthn());
        assertTrue(authnRequest.getIssueInstant().isBefore(new DateTime()));
        assertEquals(properties.getProviderName(), authnRequest.getProviderName());
        assertEquals(singleSignOnService.getLocation(), authnRequest.getDestination());
        assertEquals(SAMLConstants.SAML2_POST_BINDING_URI, authnRequest.getProtocolBinding());
        assertEquals(properties.getCallbackUrl(), authnRequest.getAssertionConsumerServiceURL());
        assertEquals(properties.getSpEntityId(), authnRequest.getIssuer().getValue());
        assertNameIDPolicy(authnRequest.getNameIDPolicy());

        RequestedAuthnContext(authnRequest.getRequestedAuthnContext());

        assertNotNull(authnRequest.getSignature().getSigningCredential());
        assertNotNull(authnRequest.getSignature().getKeyInfo().getX509Datas().get(0).getX509Certificates().get(0));

        List<XMLObject> extensions = authnRequest.getExtensions().getUnknownXMLObjects();
        assertExtensions(extensions);

        Assert.assertEquals(AssuranceLevel.LOW.getUri(), authnRequest.getRequestedAuthnContext().getAuthnContextClassRefs().get(0).getAuthnContextClassRef());
    }

    private void assertNameIDPolicy(NameIDPolicy nameIDPolicy) {
        assertEquals(NameIDType.UNSPECIFIED, nameIDPolicy.getFormat());
        assertTrue(nameIDPolicy.getAllowCreate());
    }

    private void RequestedAuthnContext(RequestedAuthnContext requestedAuthnContext) {
        assertEquals(AuthnContextComparisonTypeEnumeration.MINIMUM, requestedAuthnContext.getComparison());
        assertEquals(AssuranceLevel.LOW.getUri(), requestedAuthnContext.getAuthnContextClassRefs().get(0).getAuthnContextClassRef());
    }

    private void assertExtensions(List<XMLObject> extensions) {
        assertSpType(extensions.get(0));

        XMLObject requestedAttributes = extensions.get(1);
        assertRequestedAttribute(requestedAttributes.getOrderedChildren().get(0), "DateOfBirth", "http://eidas.europa.eu/attributes/naturalperson/DateOfBirth", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
        assertRequestedAttribute(requestedAttributes.getOrderedChildren().get(1), "LegalName", "http://eidas.europa.eu/attributes/legalperson/LegalName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
        assertRequestedAttribute(requestedAttributes.getOrderedChildren().get(2), "LegalPersonIdentifier", "http://eidas.europa.eu/attributes/legalperson/LegalPersonIdentifier", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
        assertRequestedAttribute(requestedAttributes.getOrderedChildren().get(3), "FamilyName", "http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
        assertRequestedAttribute(requestedAttributes.getOrderedChildren().get(4), "FirstName", "http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
        assertRequestedAttribute(requestedAttributes.getOrderedChildren().get(5), "PersonIdentifier", "http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
    }

    private void assertSpType(XMLObject spType) {
        assertEquals("SPType", spType.getDOM().getLocalName());
        Assert.assertEquals(SPType.PUBLIC.getValue(), spType.getDOM().getTextContent());
    }

    private void assertRequestedAttribute(XMLObject requestedAttribute, String expectedFriendlyName, String expectedName, String expectedNameFormat) {
        assertEquals(expectedFriendlyName, requestedAttribute.getDOM().getAttribute("FriendlyName"));
        assertEquals(expectedName, requestedAttribute.getDOM().getAttribute("Name"));
        assertEquals(expectedNameFormat, requestedAttribute.getDOM().getAttribute("NameFormat"));
        assertEquals("true", requestedAttribute.getDOM().getAttribute("isRequired"));
    }

}
