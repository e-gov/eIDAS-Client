package ee.ria.eidas;


import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.path.xml.config.XmlPathConfig.xmlPathConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ee.ria.eidas.config.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.path.xml.XmlPath;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Category(IntegrationTest.class)
public class MetadataIntegrationTest extends TestsBase {

    @Value("${eidas.client.spEntityId}")
    private String spMetadata;

    @Value("${eidas.client.spReturnUrl}")
    private String spReturnUrl;

    @Ignore
    @Test
    public  void metap1_hasValidSignature() {
        assertTrue("Signature must be intact", validateMetadataSignature(getMedatadaBody()));
    }

    @Ignore
    @Test
    public void metap2_verifySamlMetadataSchema() {
        assertTrue("Metadata must be based on urn:oasis:names:tc:SAML:2.0:metadata schema", validateMetadataSchema());
    }

    @Ignore //Not needed, because we have schema check?
    @Test
    public void metap2_verifySamlMetadataIdentifier() {
        String response = getMedatadaBody();
        XmlPath xmlPath = new XmlPath(response).using(xmlPathConfig().namespaceAware(false));
        assertEquals("The namespace should be expected", "urn:oasis:names:tc:SAML:2.0:metadata2", xmlPath.getString("EntityDescriptor.@xmlns:md"));
    }

    @Ignore
    @Test
    public void metap3_mandatoryValuesArePresentInEntityDescriptor() {
        Instant currentTime = Instant.now();
        String response = getMedatadaBody();
        XmlPath xmlPath = new XmlPath(response);
        assertEquals("The entityID must be the same as entpointUrl", spMetadata, xmlPath.getString("EntityDescriptor.@entityID"));

        Instant validUntil = Instant.parse(xmlPath.getString("EntityDescriptor.@validUntil"));
        assertThat("The metadata should be valid for 24h",currentTime.plus(Duration.ofHours(23).plusMinutes(50)), lessThan(validUntil));
    }

    @Ignore
    @Test
    public void metap3_mandatoryValuesArePresentInExtensions() {
        String response = getMedatadaBody();
        XmlPath xmlPath = new XmlPath(response);
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
    public void metap3_mandatoryValuesArePresentInSpssoDescriptor() {
        String response = getMedatadaBody();
        XmlPath xmlPath = new XmlPath(response);
        assertEquals("Authentication requests signing must be: true", "true", xmlPath.getString("EntityDescriptor.SPSSODescriptor.@AuthnRequestsSigned"));
        assertEquals("Authentication assertions signing must be: true", "true", xmlPath.getString("EntityDescriptor.SPSSODescriptor.@WantAssertionsSigned"));
        assertEquals("Enumeration must be: SAML 2.0", "urn:oasis:names:tc:SAML:2.0:protocol",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.@protocolSupportEnumeration"));
    }

    @Ignore
    @Test
    public void metap3_certificatesArePresentInSpssoDescriptorBlock() {
        String response = getMedatadaBody();
        XmlPath xmlPath = new XmlPath(response);
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
    public void metap3_nameIdFormatIsCorrectInSpssoDescriptor() {
        String response = getMedatadaBody();
        XmlPath xmlPath = new XmlPath(response);
        assertEquals("Name ID format should be: unspecified", "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.NameIDFormat"));
    }

    @Ignore
    @Test
    public void metap3_mandatoryValuesArePresentInAssertionConsumerService() {
        String response = getMedatadaBody();
        XmlPath xmlPath = new XmlPath(response);
        assertEquals("The binding must be: HTTP-POST", "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.AssertionConsumerService.@Binding"));
        assertEquals("The Location should indicate correct return url", spReturnUrl,
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.AssertionConsumerService.@Location"));
        assertEquals("The index should be: 0", "0",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.AssertionConsumerService.@index"));
        assertEquals("The isDefault shoult be: true", "0",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.AssertionConsumerService.@isDefault"));
    }

    @Ignore
    @Test
    public void metap3_organizationInformationIsCorrect() {
        String response = getMedatadaBody();
        XmlPath xmlPath = new XmlPath(response);
        assertEquals("Correct Organization name must be present", "DEMO-SP",
                xmlPath.getString("EntityDescriptor.Organization.OrganizationName"));
        assertEquals("Correct Organization display name must be present", "Sample SP",
                xmlPath.getString("EntityDescriptor.Organization.OrganizationDisplayName"));
        assertEquals("Correct Organization url must be present", "https://sp.sample/info",
                xmlPath.getString("EntityDescriptor.Organization.OrganizationURL"));
    }

    @Ignore
    @Test
    public void metap3_contacInformationIsCorrect() {
        String response = getMedatadaBody();
        XmlPath xmlPath = new XmlPath(response);
        assertEquals("Correct Organization name must be present", "eIDAS SP Operator",
                xmlPath.getString("**.findAll {it.@contactType == 'support'}.Company"));
        assertEquals("Correct Organization name must be present", "Jean-Michel",
                xmlPath.getString("**.findAll {it.@contactType == 'support'}.GivenName"));
        assertEquals("Correct Organization name must be present", "Folon",
                xmlPath.getString("**.findAll {it.@contactType == 'support'}.SurName"));
        assertEquals("Correct Organization name must be present", "contact.support@sp.eu",
                xmlPath.getString("**.findAll {it.@contactType == 'support'}.EmailAddress"));
        assertEquals("Correct Organization name must be present", "+555 123456",
                xmlPath.getString("**.findAll {it.@contactType == 'support'}.TelephoneNumber"));
    }

    @Ignore
    @Test
    public void metap4_caseSensitivityOnEndpoint() {
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get("/MeTaDaTa").then().statusCode(404); //Currently 404 is returned
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spMetadata).then().statusCode(200);
    }

    @Ignore
    @Test
    public void metap4_optionsOnMetadataEndpoint() {
        given().port(8080)
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .options(spMetadata).then().statusCode(200).header("3","s").body(equalTo("2")); //Currently 404 is returned
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spMetadata).then().statusCode(200);
    }

    @Ignore
    @Test
    public void metap4_postOnMetadataEndpoint() {
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .post(spMetadata).then().statusCode(200).body(equalTo("2")); //Currently 404 is returned
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spMetadata).then().statusCode(200);
    }

    @Ignore
    @Test
    public void metap4_putOnMetadataEndpoint() {
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .put(spMetadata).then().statusCode(200).body(equalTo("2")); //Currently 404 is returned
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spMetadata).then().statusCode(200);
    }

}
