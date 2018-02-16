package ee.ria.eidas.client.metadata;

import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.metadata.*;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.UsageType;
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

    @Before
    public void setUp() {
        metadataGenerator = new SPMetadataGenerator(properties, metadataSigningCredential, authnReqSigningCredential, responseAssertionDecryptionCredential);
    }

    @Test
    public void generatesEntityDescriptor() {
        EntityDescriptor entityDescriptor = metadataGenerator.getMetadata();
        assertEntityDescriptor(entityDescriptor);
    }

    private void assertEntityDescriptor(EntityDescriptor entityDescriptor) {
        assertEquals(properties.getSpEntityId(), entityDescriptor.getEntityID());

        assertNotNull(entityDescriptor.getSignature().getSigningCredential());
        assertNotNull(entityDescriptor.getSignature().getKeyInfo().getX509Datas().get(0).getX509Certificates().get(0));

        List<XMLObject> extensions = entityDescriptor.getExtensions().getUnknownXMLObjects();
        assertExtensions(extensions);

        assertSPSSODescriptor(entityDescriptor);
    }

    private void assertExtensions(List<XMLObject> extensions) {
        XMLObject digestMethod = extensions.get(0);
        assertEquals("http://www.w3.org/2001/04/xmlenc#sha512", digestMethod.getDOM().getAttribute("Algorithm"));
    }

    private void assertSPSSODescriptor(EntityDescriptor entityDescriptor) {
        SPSSODescriptor spDescriptor = entityDescriptor.getSPSSODescriptor(SAMLConstants.SAML20P_NS);
        assertTrue(spDescriptor.isAuthnRequestsSigned());
        assertTrue(spDescriptor.getWantAssertionsSigned());

        List<KeyDescriptor> keyDescriptors = spDescriptor.getKeyDescriptors();
        assertKeyDescriptor(keyDescriptors.get(0), UsageType.SIGNING);
        assertKeyDescriptor(keyDescriptors.get(1), UsageType.ENCRYPTION);

        List<NameIDFormat> nameIDFormats = spDescriptor.getNameIDFormats();
        assertNameIDFormat(nameIDFormats.get(0), NameIDType.TRANSIENT);
        assertNameIDFormat(nameIDFormats.get(1), NameIDType.PERSISTENT);
        assertNameIDFormat(nameIDFormats.get(2), NameIDType.UNSPECIFIED);

        AssertionConsumerService assertionConsumerService = spDescriptor.getAssertionConsumerServices().get(0);
        assertAssertionConsumerService(assertionConsumerService);
    }

    private void assertKeyDescriptor(KeyDescriptor keyDescriptor, UsageType expectedUsageType) {
        assertEquals(expectedUsageType, keyDescriptor.getUse());
        assertNotNull(keyDescriptor.getKeyInfo().getX509Datas().get(0).getX509Certificates().get(0));
    }

    private void assertNameIDFormat(NameIDFormat nameIDFormat, String expectedFormat) {
        assertEquals(expectedFormat, nameIDFormat.getFormat());
    }

    private void assertAssertionConsumerService(AssertionConsumerService assertionConsumerService) {
        assertEquals(SAMLConstants.SAML2_POST_BINDING_URI, assertionConsumerService.getBinding());
        assertEquals(properties.getCallbackUrl(), assertionConsumerService.getLocation());
        assertEquals(new Integer(0), assertionConsumerService.getIndex());
    }

}
