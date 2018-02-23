package ee.ria.eidas;

import com.sun.org.apache.xerces.internal.dom.DOMInputImpl;
import ee.ria.eidas.client.utils.XmlUtils;
import ee.ria.eidas.client.webapp.EidasClientApplication;
import ee.ria.eidas.config.SystemPropertyActiveProfileResolver;
import io.restassured.RestAssured;
import io.restassured.config.XmlConfig;
import io.restassured.path.xml.XmlPath;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.opensaml.saml.common.SignableSAMLObject;
import org.opensaml.security.credential.CredentialSupport;
import org.opensaml.security.x509.X509Credential;
import org.opensaml.security.x509.X509Support;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.util.Base64;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.internal.matcher.xml.XmlXsdMatcher.matchesXsdInClasspath;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = EidasClientApplication.class, webEnvironment= SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles( profiles={"test"}, resolver=SystemPropertyActiveProfileResolver.class)
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

    protected String getMetadataBody() {
        return given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spMetadataUrl).then().log().ifError().statusCode(200).extract().body().asString();
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
                .then().log().ifError()
                .statusCode(200)
                .body(matchesXsdInClasspath("SPschema.xsd").using(new ClasspathResourceResolver()));
        return true;
    }

    protected XmlPath getDecodedSamlRequestBodyXml(String body) {
        XmlPath html = new XmlPath(XmlPath.CompatibilityMode.HTML, body);
        String SAMLRequestString = html.getString("**.findAll { it.@name == 'SAMLRequest' }.@value");
        String decodedRequest = new String(Base64.getDecoder().decode(SAMLRequestString), StandardCharsets.UTF_8);
        XmlPath decodedSAMLrequest = new XmlPath(decodedRequest);
        return decodedSAMLrequest;
    }

    protected String getDecodedSamlRequestBody(String body) {
        XmlPath html = new XmlPath(XmlPath.CompatibilityMode.HTML, body);
        String SAMLRequestString = html.getString("**.findAll { it.@name == 'SAMLRequest' }.@value");
        String decodedSAMLrequest = new String(Base64.getDecoder().decode(SAMLRequestString), StandardCharsets.UTF_8);
        return decodedSAMLrequest;
    }

    protected String getAuthenticationReqWithDefault() {
        return getAuthenticationReq("CA", "LOW","relayState");
    }

    protected String getAuthenticationReq(String country, String loa, String relayState) {
        return given()
                .formParam("relayState",relayState)
                .formParam("loa",loa)
                .formParam("country",country)
                .contentType("application/x-www-form-urlencoded")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .post(spStartUrl).then().log().ifError().extract().body().asString();
    }

    protected String getAuthenticationReqForm(Map<String,String> values) {
        return given()
                .formParams(values)
                .contentType("application/x-www-form-urlencoded")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .post(spStartUrl).then().log().ifError().extract().body().asString();
    }

    protected String getLoginPage() {
        return given()
                .contentType("application/x-www-form-urlencoded")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .when()
                .get(spStartUrl).then().log().ifError().extract().body().asString();
    }

    protected Boolean validateSignature(String body) {
        XmlPath metadataXml = new XmlPath(body);
        try {
            java.security.cert.X509Certificate x509 = X509Support.decodeCertificate(metadataXml.getString("EntityDescriptor.Signature.KeyInfo.X509Data.X509Certificate"));
            validateSignature(body,x509);
            return true;
        } catch (CertificateException e) {
            throw new RuntimeException("Certificate parsing in validateSignature() failed:" + e.getMessage(), e);
        }
    }


    protected Boolean validateSignature(String body, java.security.cert.X509Certificate x509) {
        try {
            x509.checkValidity();
            SignableSAMLObject signableObj = XmlUtils.unmarshallElement(body);
            X509Credential credential = CredentialSupport.getSimpleCredential(x509,null);
            SignatureValidator.validate(signableObj.getSignature(), credential);
            return true;
        } catch (SignatureException e) {
            throw new RuntimeException("Signature validation in validateSignature() failed: " + e.getMessage(), e);
        } catch (CertificateNotYetValidException e) {
            throw new RuntimeException("Certificate is not yet valid: " + e.getMessage(), e);
        } catch (CertificateExpiredException e) {
            throw new RuntimeException("Certificate is expired: " + e.getMessage(), e);
        }
    }

    protected Boolean isCertificateValid(java.security.cert.X509Certificate x509) {
        try {
            x509.checkValidity();
            return true;
        } catch (CertificateExpiredException e) {
            throw new RuntimeException("Certificate is expired: " + e.getMessage(), e);
        } catch (CertificateNotYetValidException e) {
            throw new RuntimeException("Certificate is not yet valid: " + e.getMessage(), e);
        }
    }

    protected Boolean isCertificateValid(String certString) {
        try {
            java.security.cert.X509Certificate x509 = X509Support.decodeCertificate(certString);
            isCertificateValid(x509);
            x509.checkValidity();
            return true;
        } catch (CertificateExpiredException e) {
            throw new RuntimeException("Certificate is expired: " + e.getMessage(), e);
        } catch (CertificateNotYetValidException e) {
            throw new RuntimeException("Certificate is not yet valid: " + e.getMessage(), e);
        } catch (CertificateException e) {
            throw new RuntimeException("Certificate parsing in isCertificateValid() failed: " + e.getMessage(), e);
        }
    }

    public class ClasspathResourceResolver implements LSResourceResolver {
        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
            InputStream resource = ClassLoader.getSystemResourceAsStream(systemId);
            return new DOMInputImpl(publicId, systemId, baseURI, resource, null);
        }
    }

}
