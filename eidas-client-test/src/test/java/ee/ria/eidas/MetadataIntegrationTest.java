package ee.ria.eidas;


import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

import ee.ria.eidas.config.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.path.xml.XmlPath;
import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Category(IntegrationTest.class)
public class MetadataIntegrationTest extends TestsBase {

    @Value("${eidas.client.spMetadataUrl}")
    private String spMetadata;

    @Value("${eidas.client.spReturnUrl}")
    private String spReturnUrl;

    @Ignore
    @Test // This is optional block
    public void metap2_mandatoryValuesArePresentInExtensions() {
        XmlPath xmlPath = getMetadataBodyXML();

        List<String> digestMethods = xmlPath.getList("EntityDescriptor.Extensions.DigestMethod.@Algorithm");
        assertThat("One of the accepted digest algorithms must be present", digestMethods,
                anyOf(hasItem("http://www.w3.org/2001/04/xmlenc#sha512"), hasItem("http://www.w3.org/2001/04/xmlenc#sha256")));

        List<String> signingMethods = xmlPath.getList("EntityDescriptor.Extensions.SigningMethod.@Algorithm");
        assertThat("One of the accepted singing algorithms must be present", signingMethods,
                anyOf(hasItem("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512"), hasItem("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256"),
                        hasItem("http://www.w3.org/2007/05/xmldsig-more#sha512-rsa-MGF1"), hasItem("http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1")));
    }

    @Test
    public void metap3_validUtnilIsPresentInEntityDescriptor() {
        Instant currentTime = Instant.now();
        XmlPath xmlPath = getMetadataBodyXML();
        Instant validUntil = Instant.parse(xmlPath.getString("EntityDescriptor.@validUntil"));
        xmlPath = getMetadataBodyXML();
        Instant validUntil2 = Instant.parse(xmlPath.getString("EntityDescriptor.@validUntil"));
        assertThat("The metadata should be valid for 24h",currentTime.plus(Duration.ofHours(23).plusMinutes(50)), lessThan(validUntil));
        assertThat("The metadata should be valid for 24h",validUntil, allOf(lessThan(currentTime.plus(Duration.ofHours(24).plusMinutes(5))), greaterThan(currentTime.plus(Duration.ofHours(23).plusMinutes(55)))));
        assertThat("Metadata should be generated on each request", validUntil, not(equalTo(validUntil2)));
    }

    @Ignore
    @Test //This is optional block
    public void metap2_organizationInformationIsCorrect() {
        XmlPath xmlPath = getMetadataBodyXML();
        assertEquals("Correct Organization name must be present", "DEMO-SP",
                xmlPath.getString("EntityDescriptor.Organization.OrganizationName"));
        assertEquals("Correct Organization display name must be present", "Sample SP",
                xmlPath.getString("EntityDescriptor.Organization.OrganizationDisplayName"));
        assertEquals("Correct Organization url must be present", "https://sp.sample/info",
                xmlPath.getString("EntityDescriptor.Organization.OrganizationURL"));
    }

    @Ignore
    @Test //This is optional block
    public void metap2_contacInformationIsCorrect() {
        XmlPath xmlPath = getMetadataBodyXML();
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
    @Test //These values are optional
    public void metap2_optionalValuesArePresentInAssertionConsumerService() {
        XmlPath xmlPath = getMetadataBodyXML();
        assertEquals("The index should be: 0", "0",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.AssertionConsumerService.@index"));
        assertEquals("The isDefault shoult be: true", "true",
                xmlPath.getString("EntityDescriptor.SPSSODescriptor.AssertionConsumerService.@isDefault"));
    }

    @Test
    public void metap3_caseSensitivityOnEndpoint() {
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get("/SP/MeTaDaTa").then().log().ifValidationFails().statusCode(404).body("error",equalTo("Not Found"));
    }

    @Test
    public void metap3_optionsMethodShouldReturnAllowedMethods() {
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .options(spMetadata).then().log().ifValidationFails().statusCode(200).header("Allow","GET,HEAD");
    }

    @Test
    public void metap3_notSupportedHttpPostMethodShouldReturnError() {
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .post(spMetadata).then().log().ifValidationFails().statusCode(405).header("Allow","GET").body("error",Matchers.equalTo("Method Not Allowed"));
    }

    @Test
    public void metap3_notSupportedHttpPutMethodShouldReturnError() {
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .put(spMetadata).then().log().ifValidationFails().statusCode(405).header("Allow","GET").body("error",Matchers.equalTo("Method Not Allowed"));
    }

    @Test
    public void metap3_headHttpMethodShouldNotReturnBody() {
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .head(spMetadata).then().log().ifValidationFails().statusCode(200).body(isEmptyOrNullString());
    }

    @Test
    public void metap3_notSupportedHttpDeleteMethodShouldReturnError() {
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .delete(spMetadata).then().log().ifValidationFails().statusCode(405).header("Allow","GET").body("error", Matchers.equalTo("Method Not Allowed"));
    }


}
