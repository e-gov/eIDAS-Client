package ee.ria.eidas;


import ee.ria.eidas.config.IntegrationTest;
import io.restassured.path.xml.XmlPath;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
public class AuthenticationRequestIntegrationTest extends TestsBase {

    @Value("${eidas.client.idpStartUrl}")
    private String idpStartUrl;

    @Value("${eidas.client.spProviderName}")
    private String spProviderName;

    @Value("${eidas.client.spStartUrl}")
    private String spStartUrl;

    @Value("${eidas.client.spReturnUrl}")
    private String spReturnUrl;

    //TODO: We need a unified way to check the country code!
    @Ignore
    @Test
    public void auth1_countryCodeIsPresent() {
        assertTrue(true);
    }

//    @Ignore
    @Test
    public  void auth1_hasValidSignature() {
        assertTrue("Signature must be intact", validateSignature(getDecodedSamlRequestBodyXml(getAuthenticationReqBody())));
    }

    @Ignore
    @Test
    public void auth1_certificateIsPresentInSignature() {
        XmlPath xmlPath = getDecodedSamlRequestBodyXml(getAuthenticationReqBody());
        String signingCertificate = xmlPath.getString("AuthnRequest.Signature.KeyInfo.X509Data.X509Certificate");
        assertThat("Signing certificate must be present", signingCertificate, startsWith("MII"));
        assertTrue("Signing certificate must be valid", isCertificateValid(signingCertificate));
    }

    @Ignore
    @Test //TODO: Does SAML request has also schema to validate against?
    public void auth1_verifySamlAuthRequestSchema() {
        //assertTrue("Metadata must be based on urn:oasis:names:tc:SAML:2.0:metadata schema", validateMetadataSchema());
    }

    @Ignore
    @Test
    public void auth2_mandatoryValuesArePresentAndSetTrueForNaturalPersons() {
        XmlPath xmlPath = getDecodedSamlRequestBodyXml(getAuthenticationReqMinimalData());
        assertEquals("Family name must be present and required set to: true", "true",
                xmlPath.getString("**.findAll { it.@Name == 'http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName' }.@isRequired"));
        assertEquals("First name must be present and required set to: true", "true",
                xmlPath.getString("**.findAll { it.@Name == 'http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName' }.@isRequired"));
        assertEquals("Date of birth must be present and required set to: true", "true",
                xmlPath.getString("**.findAll { it.@Name == 'http://eidas.europa.eu/attributes/naturalperson/DateOfBirth' }.@isRequired"));
        assertEquals("Person identifier must be present and required set to: true", "true",
                xmlPath.getString("**.findAll { it.@Name == 'http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier' }.@isRequired"));
    }

    @Ignore
    @Test
    public void auth2_mandatoryValuesArePresent() {
        XmlPath xmlPath = getDecodedSamlRequestBodyXml(getAuthenticationReqBody());
        assertEquals("SPType must be: public", "public", xmlPath.getString("AuthnRequest.Extensions.SPType"));
        assertEquals("The NameID policy must be: unspecified", "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified", xmlPath.getString("AuthnRequest.NameIDPolicy.@Format"));
    }

    @Ignore
    @Test
    public void auth2_authenticationLevelIsPresent() {
        XmlPath xmlPath = getDecodedSamlRequestBodyXml(getAuthenticationReqBody());
        List<String> signingMethods = xmlPath.getList("AuthnRequest.RequestedAuthnContext.AuthnContextClassRef");
        assertThat("One of the accepted authentication levels must be present", signingMethods,
                anyOf(hasItem("http://eidas.europa.eu/LoA/low"), hasItem("http://eidas.europa.eu/LoA/substantial"), hasItem("http://eidas.europa.eu/LoA/high")));
    }

    @Ignore
    @Test //TODO: Need a method to validate NCName format
    public void auth3_mandatoryValuesArePresentInEntityDescriptor() {
        XmlPath xmlPath = getDecodedSamlRequestBodyXml(getAuthenticationReqBody());
        assertEquals("The Destination must be the connected eIDAS node URL", idpStartUrl, xmlPath.getString("AuthnRequest.@Destination"));
        //assertTrue("ID must be in NCName format" , validateNCNameFormat(xmlPath.getString("AuthnRequest.@ID")));
        assertEquals("The ForceAuthn must be: true", "true", xmlPath.getString("AuthnRequest.@ForceAuthn"));
        assertEquals("The IsPassive must be: false", "false", xmlPath.getString("AuthnRequest.@IsPassive"));
        assertEquals("The Version must be: 2.0", "2.0", xmlPath.getString("AuthnRequest.@Version"));
        assertEquals("ProviderName must be correct", spProviderName, xmlPath.getString("AuthnRequest.@ProviderName"));
        Instant currentTime = Instant.now();
        Instant issuingTime = Instant.parse(xmlPath.getString("AuthnRequest.@IssueInstant"));
        // This assertion may cause flakyness if the client server clock is different
        assertThat("The issuing time should be within 5 seconds of current time",issuingTime, allOf(lessThan(currentTime), greaterThan(currentTime.minus(Duration.ofMillis(5000)))));
    }




}
