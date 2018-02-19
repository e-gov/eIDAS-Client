package ee.ria.eidas.client.webapp;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.XmlConfig;
import com.jayway.restassured.response.ResponseBodyExtractionOptions;
import ee.ria.eidas.client.utils.ClasspathResourceResolver;
import ee.ria.eidas.client.utils.XmlUtils;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.matcher.RestAssuredMatchers.matchesXsdInClasspath;
import static com.jayway.restassured.path.xml.XmlPath.from;
import static org.junit.Assert.assertNotNull;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.IsEqual.equalTo;

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

        EntityDescriptor signableObj = (EntityDescriptor)XmlUtils.unmarshallElement(body.asString());
        SignatureValidator.validate(signableObj.getSignature(), metadataSigningCredential);
    }

    @Test
    public void httpPostBinding() {
        given()
            .port(port)
        .when()
            .get("/start")
        .then()
            .statusCode(200)
            .body("html.body.form.@action", equalTo("http://localhost:8080/EidasNode/ServiceProvider"))
            .body("html.body.form.div.input[0].@value", not(empty()))
            .body("html.body.form.div.input[1].@value", equalTo("CA"));
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
}
