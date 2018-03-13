package ee.ria.eidas.client.webapp;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.XmlConfig;
import com.jayway.restassured.response.ResponseBodyExtractionOptions;
import ee.ria.eidas.client.utils.ClasspathResourceResolver;
import ee.ria.eidas.client.utils.XmlUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.matcher.RestAssuredMatchers.matchesXsdInClasspath;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EidasClientApplicationTest {

    private final static WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(7771));

    @LocalServerPort
    int port;

    @Autowired
    Credential metadataSigningCredential;

    @BeforeClass
    public static void initExternalDependencies() {
        wireMockServer.start();

        wireMockServer.stubFor(WireMock.get(urlEqualTo("/EidasNode/ConnectorResponderMetadata"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(readFileBody("samples/response/idp-metadata-ok.xml"))
                ));
    }

    @Test
    public void metadataMatchesSchema() {
        given()
            .port(port).config(RestAssured.config().xmlConfig(XmlConfig.xmlConfig().disableLoadingOfExternalDtd()))
        .when()
            .get("/metadata")
        .then()
            .statusCode(200)
            .body(matchesXsdInClasspath(ClasspathResourceResolver.SCHEMA_DIR_ON_CLASSPATH + "saml-schema-metadata-2.0.xsd").using(new ClasspathResourceResolver()));
    }

    @Test
    public void metadataIsSignedWithKeyConfiguredInKeystore() throws Exception {

        ResponseBodyExtractionOptions body = given()
            .port(port)
        .when()
            .get("/metadata")
        .then()
            .statusCode(200)
            .extract().body();

        EntityDescriptor signableObj = XmlUtils.unmarshallElement(body.asString());
        SignatureValidator.validate(signableObj.getSignature(), metadataSigningCredential);
    }

    @Test
    public void httpPostBinding_shouldPass_whenAllParamsPresent() {
        given()
            .port(port)
            .queryParam("country", "EE")
            .queryParam("loa", "LOW")
            .queryParam("relayState", "test")
        .when()
            .post("/login")
        .then()
            .statusCode(200)
            .body("html.body.form.@action", equalTo("http://localhost:8080/EidasNode/ServiceProvider"))
            .body("html.body.form.div.input[0].@value", equalTo("test"))
            .body("html.body.form.div.input[1].@value", not(empty()))
            .body("html.body.form.div.input[2].@value", equalTo("EE"));
    }

    @Test
    public void httpPostBinding_shouldPass_whenOnlyCountryParamPresent() {
        given()
            .port(port)
            .queryParam("country", "EE")
        .when()
            .post("/login")
        .then()
            .statusCode(200)
            .body("html.body.form.@action", equalTo("http://localhost:8080/EidasNode/ServiceProvider"))
            .body("html.body.form.div.input[0].@value", not(empty()))
            .body("html.body.form.div.input[1].@value", equalTo("EE"));
    }

    @Test
    public void httpPostBinding_shouldFail_whenCountryRequestParamInvalid() {
        given()
                .port(port)
                .queryParam("country", "NEVERLAND")
                .queryParam("relayState", "test")
                .when()
                .post("/login")
                .then()
                .statusCode(400)
                .body("error", equalTo("Invalid country! Valid countries:[EE, CA]"));
    }

    @Test
    public void httpPostBinding_shouldFail_whenRelayStateRequestParamInvalid() {
        given()
            .port(port)
            .queryParam("country", "EE")
            .queryParam("relayState", "ä")
        .when()
            .post("/login")
        .then()
            .statusCode(400)
            .body("error", equalTo("Invalid RelayState! Must match the following regexp: ^[a-zA-Z0-9-_]{0,80}$"));
    }

    public static String readFileBody(String fileName) {
        return new String(readFileBytes(fileName), StandardCharsets.UTF_8);
    }

    public static byte[] readFileBytes(String fileName) {
        try {
            ClassLoader classLoader = EidasClientApplicationTest.class.getClassLoader();
            URL resource = classLoader.getResource(fileName);
            assertNotNull("File not found: " + fileName, resource);
            return Files.readAllBytes(Paths.get(resource.toURI()));
        } catch (Exception e) {
            throw new RuntimeException("Exception: " + e.getMessage(), e);
        }
    }

    @AfterClass
    public static void teardown() throws Exception {
        wireMockServer.stop();
    }

}
