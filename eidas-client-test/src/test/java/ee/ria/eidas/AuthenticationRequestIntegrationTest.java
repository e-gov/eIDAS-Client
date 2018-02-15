package ee.ria.eidas;


import ee.ria.eidas.config.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.path.xml.XmlPath;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.path.xml.config.XmlPathConfig.xmlPathConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
public class AuthenticationRequestIntegrationTest extends TestsBase {

    @Value("${eidas.client.spEntityId}")
    private String spMetadata;

    @Value("${eidas.client.spStartUrl}")
    private String spStartUrl;

    @Value("${eidas.client.spReturnUrl}")
    private String spReturnUrl;

//    @Ignore
    @Test
    public  void auth1_hasValidSignature() {
        assertTrue("Signature must be intact", validateSignature(getAuthenticationReqBody()));
    }

   }
