package ee.ria.eidas;

import com.sun.org.apache.xerces.internal.dom.DOMInputImpl;
import ee.ria.eidas.client.webapp.EidasClientApplication;
import io.restassured.RestAssured;
import io.restassured.config.XmlConfig;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.Response;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.w3c.dom.Node;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import sun.misc.BASE64Decoder;

import java.beans.XMLDecoder;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.internal.matcher.xml.XmlXsdMatcher.matchesXsdInClasspath;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = EidasClientApplication.class, webEnvironment= SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(locations="classpath:application-test.properties")
public class TestsBase {

    @Value("${local.server.port}")
    protected int serverPort;

    @Value("${eidas.client.spMetadataUrl}")
    protected String spMetadataUrl;

    @Value("${eidas.client.spStartUrl}")
    protected String spStartUrl;

    @Before
    public void setUp() {
        RestAssured.port = serverPort;
    }

    protected Response getMetadata() {
        return given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spMetadataUrl);
    }

    protected String getMetadataBody() {
        return given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spMetadataUrl).then().extract().body().asString();
    }

    protected XmlPath getMetadataBodyXML() {
        String metadataResponse = getMetadataBody();
        XmlPath metadataXml = new XmlPath(metadataResponse);
        return metadataXml;
    }

    protected Boolean validateMetadataSchema() {
        given()
        .config(RestAssured.config().xmlConfig(XmlConfig.xmlConfig().disableLoadingOfExternalDtd()))
                .when()
                .get(spMetadataUrl)
                .then()
                .statusCode(200)
                .body(matchesXsdInClasspath("SPschema.xsd").using(new ClasspathResourceResolver()));
        return true;
    }

    protected XmlPath getDecodedSamlRequestBodyXml(String body) {
        XmlPath html = new XmlPath(XmlPath.CompatibilityMode.HTML, body);
        String SAMLRequestString = html.getString("**.findAll { it.@name == 'SAMLRequest' }");
        String decodedRequest = new String(Base64.getDecoder().decode(SAMLRequestString), StandardCharsets.UTF_8);
        XmlPath decodedSAMLrequest = new XmlPath(decodedRequest);
        return decodedSAMLrequest;
    }

    protected String getAuthenticationReqBody() {
        return given()
                .formParam("eidasconnector","http://localhost:8080//EidasNode/ConnectorResponderMetadata")
                .formParam("nodeMetadataUrl","http://localhost:8080//EidasNode/ConnectorResponderMetadata")
                .formParam("citizenEidas","CA")
                .formParam("returnUrl","http://localhost:8080/SP/ReturnPage")
                .formParam("eidasNameIdentifier","urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified")
                .formParam("eidasloa","http://eidas.europa.eu/LoA/low")
                .formParam("eidasloaCompareType","minimum")
                .formParam("eidasSPType","public")
                .formParam("SPType","public")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/AdditionalAttribute","http://eidas.europa.eu/attributes/naturalperson/AdditionalAttribute")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/AdditionalAttributeType","false")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/BirthName","http://eidas.europa.eu/attributes/naturalperson/BirthName")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/BirthNameType","false")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/CurrentAddress","http://eidas.europa.eu/attributes/naturalperson/CurrentAddress")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/CurrentAddressType","false")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName","http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyNameType","true")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName","http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/CurrentGivenNameType","true")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/DateOfBirth","http://eidas.europa.eu/attributes/naturalperson/DateOfBirth")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/DateOfBirthType","true")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyNameType","true")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/Gender","http://eidas.europa.eu/attributes/naturalperson/Gender")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/GenderType","false")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier","http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/PlaceOfBirthType","false")
                .formParam("allTypeEidas","none")
                .contentType("application/x-www-form-urlencoded")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .post(spStartUrl).then().extract().body().asString();
    }

    protected String getAuthenticationReqMinimalData() {
        return given()
                .formParam("eidasconnector","http://localhost:8080//EidasNode/ConnectorResponderMetadata")
                .formParam("nodeMetadataUrl","http://localhost:8080//EidasNode/ConnectorResponderMetadata")
                .formParam("citizenEidas","CA")
                .formParam("returnUrl","http://localhost:8080/SP/ReturnPage")
                .formParam("eidasNameIdentifier","urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified")
                .formParam("eidasloa","http://eidas.europa.eu/LoA/low")
                .formParam("eidasloaCompareType","minimum")
                .formParam("eidasSPType","public")
                .formParam("SPType","public")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName","http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyNameType","true")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName","http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/CurrentGivenNameType","true")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/DateOfBirth","http://eidas.europa.eu/attributes/naturalperson/DateOfBirth")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/DateOfBirthType","true")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier","http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier")
                .formParam("http://eidas.europa.eu/attributes/naturalperson/PersonIdentifierType","true")
                .formParam("allTypeEidas","none")
                .contentType("application/x-www-form-urlencoded")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .post(spStartUrl).then().extract().body().asString();
    }

    //TODO: Need a method for signature validation
    protected Boolean validateSignature(XmlPath body) {
        return true;
    }

    //TODO: Need a method for certificate validity check
    protected Boolean isCertificateValid(String certString) {
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
