package ee.ria.eidas.client.metadata;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.config.EidasCredentialsConfiguration;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.saml.common.SAMLObjectContentReference;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.metadata.*;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.UsageType;
import org.opensaml.xmlsec.signature.Signature;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { EidasClientConfiguration.class, EidasCredentialsConfiguration.class })
@TestPropertySource(locations = "classpath:application-test.properties")
public class SPMetadataGeneratorTest {

    private SPMetadataGenerator metadataGenerator;

    @Autowired
    private EidasClientProperties properties;

    @Autowired
    private Credential metadataSigningCredential;

    @Autowired
    private Credential authnReqSigningCredential;

    @Autowired
    private Credential responseAssertionDecryptionCredential;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private Appender mockedAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventCaptor;

    @Before
    public void setUp() {
        metadataGenerator = new SPMetadataGenerator(properties, metadataSigningCredential, authnReqSigningCredential, responseAssertionDecryptionCredential, applicationEventPublisher);

        Logger root = (Logger) LoggerFactory.getLogger(SPMetadataGenerator.class);
        root.addAppender(mockedAppender);
        root.setLevel(Level.DEBUG);
    }

    @Test
    public void generatesEntityDescriptor() {
        EntityDescriptor entityDescriptor = metadataGenerator.getMetadata();
        assertEntityDescriptor(entityDescriptor);

        verifyLogs("Successfully generated metadata. Metadata ID: " + entityDescriptor.getID(), Level.INFO);
        verifyLogs("Generated metadata: " + OpenSAMLUtils.getXmlString(entityDescriptor), Level.DEBUG);
    }

    private void assertEntityDescriptor(EntityDescriptor entityDescriptor) {
        assertEquals(properties.getSpEntityId(), entityDescriptor.getEntityID());
        assertTrue(entityDescriptor.getValidUntil().isBefore(Instant.now().plus(Duration.ofDays(properties.getMetadataValidityInDays()))));

        assertSignature(entityDescriptor.getSignature());
        assertExtensions(entityDescriptor.getExtensions());
        assertSPSSODescriptor(entityDescriptor);
    }

    private void assertSignature(Signature signature) {
        assertNotNull(signature.getSigningCredential());
        assertNotNull(signature.getKeyInfo().getX509Datas().get(0).getX509Certificates().get(0));
        assertEquals(properties.getMetadataSignatureAlgorithm(), signature.getSignatureAlgorithm());
        assertEquals(OpenSAMLUtils.getRelatedDigestAlgorithm(properties.getMetadataSignatureAlgorithm()).getURI(), ((SAMLObjectContentReference) signature.getContentReferences().get(0)).getDigestAlgorithm());
    }

    private void assertExtensions(Extensions extensions) {
        List<XMLObject> extensionObjects = extensions.getUnknownXMLObjects();
        XMLObject signingMethod = extensionObjects.get(0);
        assertEquals("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512", signingMethod.getDOM().getAttribute("Algorithm"));
    }

    private void assertSPSSODescriptor(EntityDescriptor entityDescriptor) {
        SPSSODescriptor spDescriptor = entityDescriptor.getSPSSODescriptor(SAMLConstants.SAML20P_NS);
        assertTrue(spDescriptor.isAuthnRequestsSigned());
        assertTrue(spDescriptor.getWantAssertionsSigned());

        List<KeyDescriptor> keyDescriptors = spDescriptor.getKeyDescriptors();
        assertKeyDescriptor(keyDescriptors.get(0), UsageType.SIGNING);
        assertKeyDescriptor(keyDescriptors.get(1), UsageType.ENCRYPTION);

        List<NameIDFormat> nameIDFormats = spDescriptor.getNameIDFormats();
        assertNameIDFormat(nameIDFormats.get(0), NameIDType.UNSPECIFIED);

        AssertionConsumerService assertionConsumerService = spDescriptor.getAssertionConsumerServices().get(0);
        assertAssertionConsumerService(assertionConsumerService);
    }

    private void assertKeyDescriptor(KeyDescriptor keyDescriptor, UsageType expectedUsageType) {
        assertEquals(expectedUsageType, keyDescriptor.getUse());
        assertNotNull(keyDescriptor.getKeyInfo().getX509Datas().get(0).getX509Certificates().get(0));
    }

    private void assertNameIDFormat(NameIDFormat nameIDFormat, String expectedFormat) {
        assertEquals(expectedFormat, nameIDFormat.getURI());
    }

    private void assertAssertionConsumerService(AssertionConsumerService assertionConsumerService) {
        assertEquals(SAMLConstants.SAML2_POST_BINDING_URI, assertionConsumerService.getBinding());
        assertEquals(properties.getCallbackUrl(), assertionConsumerService.getLocation());
        assertEquals(Integer.valueOf(0), assertionConsumerService.getIndex());
    }

    private void verifyLogs(String logMessage, Level level) {
        verify(mockedAppender, atLeastOnce()).doAppend(loggingEventCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();

        assertTrue(loggingEvents.stream().anyMatch(event ->
                event.getFormattedMessage().contains(logMessage) && event.getLevel() == level
        ));
    }

}
