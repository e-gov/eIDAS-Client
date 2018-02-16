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

@Category(IntegrationTest.class)
public class MetadataIntegrationTest extends TestsBase {

    @Value("${eidas.client.spMetadataUrl}")
    private String spMetadata;

    @Value("${eidas.client.spReturnUrl}")
    private String spReturnUrl;

    @Ignore
    @Test
    public void metap3_validUtnilIsPresentInEntityDescriptor() {
        Instant currentTime = Instant.now();
        XmlPath xmlPath = getMetadataBodyXML();
        Instant validUntil = Instant.parse(xmlPath.getString("EntityDescriptor.@validUntil"));
        assertThat("The metadata should be valid for 24h",currentTime.plus(Duration.ofHours(23).plusMinutes(50)), lessThan(validUntil));
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
    @Test
    public void metap3_caseSensitivityOnEndpoint() {
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get("/SP/MeTaDaTa").then().statusCode(404); //Currently 404 is returned
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spMetadata).then().statusCode(200);
    }

    @Ignore
    @Test //Should return GET?
    public void metap3_optionsOnMetadataEndpoint() {
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .options(spMetadata).then().statusCode(200).header("3","s").body(equalTo("2"));
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spMetadata).then().statusCode(200);
    }

    @Ignore
    @Test //should return 404?
    public void metap3_postOnMetadataEndpoint() {
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .post(spMetadata).then().statusCode(200).body(equalTo("2"));
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spMetadata).then().statusCode(200);
    }

    @Ignore
    @Test //should return 404?
    public void metap3_putOnMetadataEndpoint() {
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .put(spMetadata).then().statusCode(200).body(equalTo("2"));
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spMetadata).then().statusCode(200);
    }

    @Ignore
    @Test
    public void metap3_headOnMetadataEndpoint() {
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .head(spMetadata).then().statusCode(200).body(isEmptyOrNullString());
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spMetadata).then().statusCode(200);
    }

    @Ignore
    @Test //what should be the output for that?
    public void metap3_deleteOnMetadataEndpoint() {
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .delete(spMetadata).then().statusCode(200).body(equalTo("2"));
        given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spMetadata).then().statusCode(200);
    }


}
