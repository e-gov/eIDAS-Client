package ee.ria.eidas;


import ee.ria.eidas.config.IntegrationTest;

import io.restassured.path.xml.XmlPath;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Value;

import static io.restassured.path.xml.config.XmlPathConfig.xmlPathConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
public class CommonMetadataIntegrationTest extends TestsBase {

    @Value("${eidas.client.spMetadataUrl}")
    private String spMetadataUrl;

    @Value("${eidas.client.spReturnUrl}")
    private String spReturnUrl;


    @Test
    public  void metap1_hasValidSignature() {
        try {
            validateSignature(getMetadataBody());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Metadata must have valid signature:  " + e.getMessage());
        }
    }

    @Test
    public void metap1_verifySamlMetadataSchema() {
        assertTrue("Metadata must be based on urn:oasis:names:tc:SAML:2.0:metadata schema", validateMetadataSchema());
    }

    @Test //TODO: Not needed, because we have schema check?
    public void metap1_verifySamlMetadataIdentifier() {
        String response = getMetadataBody();
        XmlPath xmlPath = new XmlPath(response).using(xmlPathConfig().namespaceAware(false));
        assertEquals("The namespace should be expected", "urn:oasis:names:tc:SAML:2.0:metadata", xmlPath.getString("EntityDescriptor.@xmlns:md"));
    }

    @Test
    public void metap2_mandatoryValuesArePresentInEntityDescriptor() {
        XmlPath xmlPath = getMetadataBodyXML();
        assertThat("The entityID must be the same as entpointUrl", xmlPath.getString("EntityDescriptor.@entityID"), endsWith(spMetadataUrl));
    }

    @Test
    public void metap2_mandatoryValuesArePresentInExtensions() {
        XmlPath xmlPath = getMetadataBodyXML();
        assertEquals("ServiceProvider should be public", "public", xmlPath.getString("EntityDescriptor.Extensions.SPType"));
    }

    @Test
    public void metap2_mandatoryValuesArePresentInSpssoDescriptor() {
        XmlPath xmlPath = getMetadataBodyXML();
        assertEquals("Authentication requests signing must be: true", "true", xmlPath.getString("EntityDescriptor.SPSSODescriptor.@AuthnRequestsSigned"));
        assertEquals("Authentication assertions signing must be: true", "true", xmlPath.getString("EntityDescriptor.SPSSODescriptor.@WantAssertionsSigned"));
        assertEquals("Enumeration must be: SAML 2.0", "urn:oasis:names:tc:SAML:2.0:protocol",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.@protocolSupportEnumeration"));
    }

    @Ignore //TODO: Signing and encryption certificates must be different
    @Test
    public void metap2_certificatesArePresentInSpssoDescriptorBlock() {
        XmlPath xmlPath = getMetadataBodyXML();
        String signingCertificate = xmlPath.getString("**.findAll {it.@use == 'signing'}.KeyInfo.X509Data.X509Certificate");
        String encryptionCertificate = xmlPath.getString("**.findAll {it.@use == 'encryption'}.KeyInfo.X509Data.X509Certificate");
        assertThat("Signing certificate must be present", signingCertificate, startsWith("MII"));
        assertTrue("Signing certificate must be valid", isCertificateValid(signingCertificate));
        assertThat("Encryption certificate must be present", encryptionCertificate, startsWith("MII"));
        assertTrue("Encryption certificate must be valid", isCertificateValid(encryptionCertificate));
        assertThat("Signing and encryption certificates must be different", signingCertificate, not(equalTo(encryptionCertificate)));
    }

    @Test
    public void metap2_nameIdFormatIsCorrectInSpssoDescriptor() {
        XmlPath xmlPath = getMetadataBodyXML();
        assertEquals("Name ID format should be: unspecified", "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.NameIDFormat"));
    }

    @Test
    public void metap2_mandatoryValuesArePresentInAssertionConsumerService() {
        XmlPath xmlPath = getMetadataBodyXML();
        assertEquals("The binding must be: HTTP-POST", "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.AssertionConsumerService.@Binding"));
        assertThat("The Location should indicate correct return url",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.AssertionConsumerService.@Location"), endsWith( spReturnUrl));
    }
}
