package ee.ria.eidas;

import com.sun.org.apache.xerces.internal.dom.DOMInputImpl;
import ee.ria.eidas.client.webapp.EidasClientApplication;
import io.restassured.RestAssured;
import io.restassured.config.XmlConfig;
import io.restassured.response.Response;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import java.io.InputStream;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.internal.matcher.xml.XmlXsdMatcher.matchesXsdInClasspath;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = EidasClientApplication.class, webEnvironment= SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(locations="classpath:application-test.properties")
public class TestsBase {

    @Value("${local.server.port}")
    protected int serverPort;

    @Value("${eidas.client.spEntityId}")
    protected String spMetadata;

    @Before
    public void setUp() {
        RestAssured.port = serverPort;
    }

    protected Response getMetadata() {
        return given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spMetadata);
    }

    protected String getMedatadaBody() {
        return given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spMetadata).then().extract().body().asString();
    }

    protected Boolean validateMetadataSchema() {
        given()
        .config(RestAssured.config().xmlConfig(XmlConfig.xmlConfig().disableLoadingOfExternalDtd()))
                .when()
                .get(spMetadata)
                .then()
                .statusCode(200)
                .body(matchesXsdInClasspath("SPschema.xsd").using(new ClasspathResourceResolver()));
        return true;
    }

    //TODO: Need a method for signature validation
    protected Boolean validateMetadataSignature(String body) {
        return true;
    }
    //TODO: Need a method for certificate validity check
    protected Boolean isCertificateValid(String cert) {
        return true;
    }


    public class ClasspathResourceResolver implements LSResourceResolver {
        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
            InputStream resource = ClassLoader.getSystemResourceAsStream(systemId);
            return new DOMInputImpl(publicId, systemId, baseURI, resource, null);
        }
    }

}
