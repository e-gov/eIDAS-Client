package ee.ria.eidas.client.authnrequest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.config.EidasCredentialsConfiguration;
import ee.ria.eidas.client.metadata.IDPMetadataResolver;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.saml.common.SAMLObjectContentReference;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.AuthnContextComparisonTypeEnumeration;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.NameIDPolicy;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.signature.Signature;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { EidasClientConfiguration.class, EidasCredentialsConfiguration.class })
@TestPropertySource(locations = "classpath:application-test.properties")
public class AuthnRequestBuilderTest {

    @Autowired
    private EidasClientProperties properties;

    @Autowired
    private Credential authnReqSigningCredential;

    @Autowired
    private IDPMetadataResolver idpMetadataResolver;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    private AuthnRequestBuilder requestBuilder;

    @Mock
    private Appender mockedAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventCaptor;

    @Before
    public void setUp() {
        requestBuilder = new AuthnRequestBuilder(authnReqSigningCredential, properties, idpMetadataResolver.getSingeSignOnService(), applicationEventPublisher);

        Logger root = (Logger) LoggerFactory.getLogger(AuthnRequestBuilder.class);
        root.addAppender(mockedAppender);
        root.setLevel(Level.DEBUG);
    }

    @Test
    public void buildAuthnRequest() {
        List<EidasAttribute> requestEidasAttributes = Arrays.asList(EidasAttribute.CURRENT_GIVEN_NAME, EidasAttribute.CURRENT_FAMILY_NAME, EidasAttribute.GENDER);
        AuthnRequest authnRequest = requestBuilder.buildAuthnRequest(AssuranceLevel.SUBSTANTIAL, requestEidasAttributes);

        assertAuthnRequest(authnRequest, requestEidasAttributes);

        InputStream authnRequestInputStream = new ByteArrayInputStream(OpenSAMLUtils.getXmlString(authnRequest).getBytes());
        InputStream schemaInputStream = getClass().getResourceAsStream("/saml-schema-protocol-2.0");
        validateXMLAgainstSchema(authnRequestInputStream, schemaInputStream);

        verifyLogs("AuthnRequest building succeeded", Level.INFO);
        verifyLogs("AuthnRequest: " + OpenSAMLUtils.getXmlString(authnRequest), Level.DEBUG);
    }

    @Test
    public void buildAuthnRequestWithNoEidasAttributes() {
        List<EidasAttribute> requestedEidasAttributes = Collections.emptyList();
        AuthnRequest authnRequest = requestBuilder.buildAuthnRequest(AssuranceLevel.SUBSTANTIAL, requestedEidasAttributes);

        assertAuthnRequest(authnRequest, requestedEidasAttributes);

        InputStream authnRequestInputStream = new ByteArrayInputStream(OpenSAMLUtils.getXmlString(authnRequest).getBytes());
        InputStream schemaInputStream = getClass().getResourceAsStream("/saml-schema-protocol-2.0");
        validateXMLAgainstSchema(authnRequestInputStream, schemaInputStream);

        verifyLogs("AuthnRequest building succeeded", Level.INFO);
        verifyLogs("AuthnRequest: " + OpenSAMLUtils.getXmlString(authnRequest), Level.DEBUG);
    }

    @Test
    public void buildAuthnRequestWithAllEidasAttributes() {
        List<EidasAttribute> requestedEidasAttributes = Arrays.asList(EidasAttribute.values());
        AuthnRequest authnRequest = requestBuilder.buildAuthnRequest(AssuranceLevel.SUBSTANTIAL, requestedEidasAttributes);

        assertAuthnRequest(authnRequest, requestedEidasAttributes);

        InputStream authnRequestInputStream = new ByteArrayInputStream(OpenSAMLUtils.getXmlString(authnRequest).getBytes());
        InputStream schemaInputStream = getClass().getResourceAsStream("/saml-schema-protocol-2.0");
        validateXMLAgainstSchema(authnRequestInputStream, schemaInputStream);

        verifyLogs("AuthnRequest building succeeded. Request ID: " + authnRequest.getID(), Level.INFO);
        verifyLogs("AuthnRequest: " + OpenSAMLUtils.getXmlString(authnRequest), Level.DEBUG);
    }

    private void assertAuthnRequest(AuthnRequest authnRequest, List<EidasAttribute> eidasAttributes) {
        assertTrue(authnRequest.isForceAuthn());
        assertTrue(authnRequest.getIssueInstant().isBefore(new DateTime()));
        assertEquals(properties.getProviderName(), authnRequest.getProviderName());
        assertEquals(idpMetadataResolver.getSingeSignOnService().getLocation(), authnRequest.getDestination());
        assertEquals(SAMLConstants.SAML2_POST_BINDING_URI, authnRequest.getProtocolBinding());
        assertEquals(properties.getCallbackUrl(), authnRequest.getAssertionConsumerServiceURL());
        assertEquals(properties.getSpEntityId(), authnRequest.getIssuer().getValue());
        assertNameIDPolicy(authnRequest.getNameIDPolicy());

        RequestedAuthnContext(authnRequest.getRequestedAuthnContext());

        assertSignature(authnRequest.getSignature());

        List<XMLObject> extensions = authnRequest.getExtensions().getUnknownXMLObjects();
        assertExtensions(extensions, eidasAttributes);
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

    private void assertExtensions(List<XMLObject> extensions, List<EidasAttribute> eidasAttributes) {
        assertSpType(extensions.get(0));

        List<XMLObject> requestedAttributes = extensions.get(1).getOrderedChildren();
        assertSame(eidasAttributes.size(), requestedAttributes.size());
        for (int i = 0; i < eidasAttributes.size(); i++) {
            assertRequestedAttribute(requestedAttributes.get(i), eidasAttributes.get(i));
        }
    }

    private void assertSpType(XMLObject spType) {
        assertEquals("SPType", spType.getDOM().getLocalName());
        Assert.assertEquals(SPType.PUBLIC.getValue(), spType.getDOM().getTextContent());
    }

    private void assertRequestedAttribute(XMLObject requestedAttribute, EidasAttribute eidasAttribute) {
        assertEquals(eidasAttribute.getFriendlyName(), requestedAttribute.getDOM().getAttribute("FriendlyName"));
        assertEquals(eidasAttribute.getName(), requestedAttribute.getDOM().getAttribute("Name"));
        assertEquals(AuthnRequestBuilder.REQUESTED_ATTRIBUTE_NAME_FORMAT, requestedAttribute.getDOM().getAttribute("NameFormat"));
        assertEquals(eidasAttribute.isRequired() ? "true" : "false", requestedAttribute.getDOM().getAttribute("isRequired"));
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

    private void verifyLogs(String logMessage, Level level) {
        verify(mockedAppender, atLeastOnce()).doAppend(loggingEventCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertTrue(loggingEvents.stream().anyMatch(event ->
                event.getFormattedMessage().contains(logMessage) && event.getLevel() == level
        ));
    }

}
