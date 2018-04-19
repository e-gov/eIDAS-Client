package ee.ria.eidas.client.authnrequest;

import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.saml.common.SAMLObjectContentReference;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.*;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.signature.Signature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = EidasClientConfiguration.class)
@TestPropertySource(locations = "classpath:application-test.properties")
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
        AuthnRequest authnRequest = requestBuilder.buildAuthnRequest(AssuranceLevel.SUBSTANTIAL, null);

        assertAuthnRequest(authnRequest);

        InputStream authnRequestInputStream = new ByteArrayInputStream(OpenSAMLUtils.getXmlString(authnRequest).getBytes());
        InputStream schemaInputStream = getClass().getResourceAsStream("/saml-schema-protocol-2.0");
        validateXMLAgainstSchema(authnRequestInputStream, schemaInputStream);
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

        assertSignature(authnRequest.getSignature());

        List<XMLObject> extensions = authnRequest.getExtensions().getUnknownXMLObjects();
        assertExtensions(extensions);
    }

    private void assertSignature(Signature signature) {
        assertNotNull(signature.getSigningCredential());
        assertNotNull(signature.getKeyInfo().getX509Datas().get(0).getX509Certificates().get(0));
        assertEquals(properties.getRequestSignatureAlgorithm(), signature.getSignatureAlgorithm());
        assertEquals(OpenSAMLUtils.getRelatedDigestAlgorithm(properties.getRequestSignatureAlgorithm()).getURI(), ((SAMLObjectContentReference) signature.getContentReferences().get(0)).getDigestAlgorithm());
    }

    private void assertNameIDPolicy(NameIDPolicy nameIDPolicy) {
        assertEquals(NameIDType.UNSPECIFIED, nameIDPolicy.getFormat());
        assertTrue(nameIDPolicy.getAllowCreate());
    }

    private void RequestedAuthnContext(RequestedAuthnContext requestedAuthnContext) {
        assertEquals(AuthnContextComparisonTypeEnumeration.MINIMUM, requestedAuthnContext.getComparison());
        assertEquals(AssuranceLevel.SUBSTANTIAL.getUri(), requestedAuthnContext.getAuthnContextClassRefs().get(0).getAuthnContextClassRef());
    }

    private void assertExtensions(List<XMLObject> extensions) {
        assertSpType(extensions.get(0));

        XMLObject requestedAttributes = extensions.get(1);
        assertRequestedAttribute(requestedAttributes.getOrderedChildren().get(0), "FirstName", "http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
        assertRequestedAttribute(requestedAttributes.getOrderedChildren().get(1), "FamilyName", "http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
        assertRequestedAttribute(requestedAttributes.getOrderedChildren().get(2), "PersonIdentifier", "http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
        assertRequestedAttribute(requestedAttributes.getOrderedChildren().get(3), "DateOfBirth", "http://eidas.europa.eu/attributes/naturalperson/DateOfBirth", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
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

    private boolean validateXMLAgainstSchema(InputStream xml, InputStream xsd) {
        try {
            SchemaFactory factory =
                    SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new StreamSource(xsd));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(xml));
        } catch (IOException | SAXException e) {
            return false;
        }
        return true;
    }

}
