package ee.ria.eidas;


import ee.ria.eidas.config.IntegrationTest;
import io.restassured.path.xml.XmlPath;
import org.hamcrest.text.MatchesPattern;
import org.junit.Assert;
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

@Category(IntegrationTest.class)
public class CommonAuthenticationRequestIntegrationTest extends TestsBase {

    @Value("${eidas.client.idpStartUrl}")
    private String idpStartUrl;

    @Value("${eidas.client.spProviderName}")
    private String spProviderName;

    @Value("${eidas.client.spStartUrl}")
    private String spStartUrl;

    @Value("${eidas.client.spReturnUrl}")
    private String spReturnUrl;

    @Test
    public  void auth1_hasValidSignature() {
        try {
            validateSignature(getDecodedSamlRequestBody(getAuthenticationReqWithDefault()));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Authentication request must have valid signature:  " + e.getMessage());
        }
    }

    @Test
    public void auth1_parametersArePresent() {
        XmlPath html = new XmlPath(XmlPath.CompatibilityMode.HTML, getAuthenticationReq("EE", "LOW", "relayState"));
        assertEquals("Country code is present","EE", html.getString("**.findAll { it.@name == 'country' }.@value"));
        assertEquals("RelayState is present","relayState", html.getString("**.findAll { it.@name == 'RelayState' }.@value"));
    }

    @Ignore
    @Test //TODO: Does SAML request has also schema to validate against?
    public void auth1_verifySamlAuthRequestSchema() {
        //assertTrue("Metadata must be based on urn:oasis:names:tc:SAML:2.0:metadata schema", validateMetadataSchema());
    }

    @Test
    public void auth2_mandatoryAttributessArePresentAndSetTrueForNaturalPersons() {
        XmlPath xmlPath = getDecodedSamlRequestBodyXml(getAuthenticationReqWithDefault());
        assertEquals("Family name must be present and required set to: true", "true",
                xmlPath.getString("**.findAll { it.@Name == 'http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName' }.@isRequired"));
        assertEquals("First name must be present and required set to: true", "true",
                xmlPath.getString("**.findAll { it.@Name == 'http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName' }.@isRequired"));
        assertEquals("Date of birth must be present and required set to: true", "true",
                xmlPath.getString("**.findAll { it.@Name == 'http://eidas.europa.eu/attributes/naturalperson/DateOfBirth' }.@isRequired"));
        assertEquals("Person identifier must be present and required set to: true", "true",
                xmlPath.getString("**.findAll { it.@Name == 'http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier' }.@isRequired"));
    }

    @Test
    public void auth2_mandatoryValuesArePresent() {
        XmlPath xmlPath = getDecodedSamlRequestBodyXml(getAuthenticationReqWithDefault());
        assertEquals("SPType must be: public", "public", xmlPath.getString("AuthnRequest.Extensions.SPType"));
        assertEquals("The NameID policy must be: unspecified", "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified", xmlPath.getString("AuthnRequest.NameIDPolicy.@Format"));
        assertThat("Issuer must point to Metadata url", xmlPath.getString("AuthnRequest.Issuer"), endsWith(spMetadataUrl));
    }

    @Test
    public void auth2_authenticationLevelIsPresent() {
        XmlPath xmlPath = getDecodedSamlRequestBodyXml(getAuthenticationReqWithDefault());
        List<String> loa = xmlPath.getList("AuthnRequest.RequestedAuthnContext.AuthnContextClassRef");
        assertThat("One of the accepted authentication levels must be present", loa,
                anyOf(hasItem("http://eidas.europa.eu/LoA/low"), hasItem("http://eidas.europa.eu/LoA/substantial"), hasItem("http://eidas.europa.eu/LoA/high")));
    }

    @Ignore //TODO: IsPassive value is missing
    @Test
    public void auth3_mandatoryValuesArePresentInEntityDescriptor() {
        XmlPath xmlPath = getDecodedSamlRequestBodyXml(getAuthenticationReqWithDefault());
        assertEquals("The Destination must be the connected eIDAS node URL", idpStartUrl, xmlPath.getString("AuthnRequest.@Destination"));
        assertThat("ID must be in NCName format" ,  xmlPath.getString("AuthnRequest.@ID"), MatchesPattern.matchesPattern("^[a-zA-Z_]*$"));//This regex may not be proper.
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
