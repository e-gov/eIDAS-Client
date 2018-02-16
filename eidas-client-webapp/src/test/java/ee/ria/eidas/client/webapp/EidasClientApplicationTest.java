package ee.ria.eidas.client.webapp;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.XmlConfig;
import com.sun.org.apache.xerces.internal.dom.DOMInputImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import java.io.File;
import java.io.InputStream;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.matcher.RestAssuredMatchers.matchesXsdInClasspath;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.IsEqual.equalTo;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EidasClientApplicationTest {


    public static final String SCHEMA_DIR_ON_CLASSPATH = "schema" + File.separator;

    @LocalServerPort
    int port;

    @Test
    public void metadataMatchesSchema() {
        given()
            .port(port).config(RestAssured.config().xmlConfig(XmlConfig.xmlConfig().disableLoadingOfExternalDtd()))
        .when()
            .get("/metadata")
        .then()
            .statusCode(200)
            .body(matchesXsdInClasspath(SCHEMA_DIR_ON_CLASSPATH + "saml-schema-metadata-2.0.xsd").using(new ClasspathResourceResolver()));
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

    public class ClasspathResourceResolver implements LSResourceResolver {
        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
            InputStream resource = ClassLoader.getSystemResourceAsStream(SCHEMA_DIR_ON_CLASSPATH + systemId);
            return new DOMInputImpl(publicId, systemId, baseURI, resource, null);
        }
    }
}
