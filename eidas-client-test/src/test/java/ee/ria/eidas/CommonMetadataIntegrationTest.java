package ee.ria.eidas;


import ee.ria.eidas.config.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.path.xml.XmlPath;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
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

//    @Ignore
    @Test
    public  void metap1_hasValidSignature() {
        assertTrue("Signature must be intact", validateSignature(getMetadataBodyXML()));
    }

    @Ignore
    @Test
    public void metap1_certificateIsPresentInSignature() {
        XmlPath xmlPath = getMetadataBodyXML();
        String signingCertificate = xmlPath.getString("EntityDescriptor.Signature.KeyInfo.X509Data.X509Certificate");
        assertThat("Signing certificate must be present", signingCertificate, startsWith("MII"));
        assertTrue("Signing certificate must be valid", isCertificateValid(signingCertificate));
    }

    @Ignore
    @Test
    public void metap1_verifySamlMetadataSchema() {
        assertTrue("Metadata must be based on urn:oasis:names:tc:SAML:2.0:metadata schema", validateMetadataSchema());
    }

    @Ignore //Not needed, because we have schema check?
    @Test
    public void metap1_verifySamlMetadataIdentifier() {
        String response = getMetadataBody();
        XmlPath xmlPath = new XmlPath(response).using(xmlPathConfig().namespaceAware(false));
        assertEquals("The namespace should be expected", "urn:oasis:names:tc:SAML:2.0:metadata2", xmlPath.getString("EntityDescriptor.@xmlns:md"));
    }

    @Ignore
    @Test
    public void metap2_mandatoryValuesArePresentInEntityDescriptor() {
        XmlPath xmlPath = getMetadataBodyXML();
        assertEquals("The entityID must be the same as entpointUrl", spMetadataUrl, xmlPath.getString("EntityDescriptor.@entityID"));
    }

    @Ignore
    @Test
    public void metap2_mandatoryValuesArePresentInExtensions() {
        XmlPath xmlPath = getMetadataBodyXML();
        assertEquals("ServiceProvider should be public", "public", xmlPath.getString("EntityDescriptor.Extensions.SPType"));

        List<String> digestMethods = xmlPath.getList("EntityDescriptor.Extensions.DigestMethod.@Algorithm");
        assertThat("One of the accepted digest algorithms must be present", digestMethods,
                anyOf(hasItem("http://www.w3.org/2001/04/xmlenc#sha512"), hasItem("http://www.w3.org/2001/04/xmlenc#sha256")));

        List<String> signingMethods = xmlPath.getList("EntityDescriptor.Extensions.SigningMethod.@Algorithm");
        assertThat("One of the accepted singing algorithms must be present", signingMethods,
                anyOf(hasItem("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512"), hasItem("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256"),
                        hasItem("http://www.w3.org/2007/05/xmldsig-more#sha512-rsa-MGF1"), hasItem("http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1")));
    }

    @Ignore
    @Test
    public void metap2_mandatoryValuesArePresentInSpssoDescriptor() {
        XmlPath xmlPath = getMetadataBodyXML();
        assertEquals("Authentication requests signing must be: true", "true", xmlPath.getString("EntityDescriptor.SPSSODescriptor.@AuthnRequestsSigned"));
        assertEquals("Authentication assertions signing must be: true", "true", xmlPath.getString("EntityDescriptor.SPSSODescriptor.@WantAssertionsSigned"));
        assertEquals("Enumeration must be: SAML 2.0", "urn:oasis:names:tc:SAML:2.0:protocol",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.@protocolSupportEnumeration"));
    }

    @Ignore
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

    @Ignore
    @Test
    public void metap2_nameIdFormatIsCorrectInSpssoDescriptor() {
        XmlPath xmlPath = getMetadataBodyXML();
        assertEquals("Name ID format should be: unspecified", "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.NameIDFormat"));
    }

    @Ignore
    @Test
    public void metap2_mandatoryValuesArePresentInAssertionConsumerService() {
        XmlPath xmlPath = getMetadataBodyXML();
        assertEquals("The binding must be: HTTP-POST", "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.AssertionConsumerService.@Binding"));
        assertEquals("The Location should indicate correct return url", spReturnUrl,
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.AssertionConsumerService.@Location"));
        assertEquals("The index should be: 0", "0",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.AssertionConsumerService.@index"));
        assertEquals("The isDefault shoult be: true", "true",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.AssertionConsumerService.@isDefault"));
    }
}
