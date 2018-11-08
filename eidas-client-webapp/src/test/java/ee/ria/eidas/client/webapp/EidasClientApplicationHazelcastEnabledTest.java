package ee.ria.eidas.client.webapp;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.hazelcast.core.HazelcastInstance;
import com.jayway.restassured.http.ContentType;
import ee.ria.eidas.client.utils.XmlUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.matcher.RestAssuredMatchers.matchesXsd;
import static ee.ria.eidas.client.session.HazelcastRequestSessionServiceImpl.UNANSWERED_REQUESTS_MAP;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application-hazelcast-enabled.properties")
public class EidasClientApplicationHazelcastEnabledTest {

    private final static WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(7772));

    @LocalServerPort
    int port;

    @BeforeClass
    public static void initExternalDependencies() {
        wireMockServer.start();

        wireMockServer.stubFor(WireMock.get(urlEqualTo("/EidasNode/ConnectorResponderMetadata"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(XmlUtils.readFileBody("samples/response/idp-metadata-ok.xml"))
                ));
    }

    @Test
    public void hazelcast_shouldSucceed_whenServerIsUp() {
        given()
                .port(port)
        .when()
                .get("/hazelcast")
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("clusterState", notNullValue())
                .body("clusterSize", equalTo(1))
                .body("maps[0].mapName", equalTo(UNANSWERED_REQUESTS_MAP));
    }

    @Test
    public void heartbeat_shouldExposeHazelcastInDependencies_whenServerIsUp() {
        given()
                .port(port)
        .when()
                .get("/heartbeat")
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", notNullValue())
                .body("version", notNullValue())
                .body("buildTime", notNullValue())
                .body("startTime", notNullValue())
                .body("currentTime", notNullValue())
                .body("status", equalTo("UP"))
                .body("dependencies", hasSize(2))
                .body("dependencies[0].name", equalTo("eIDAS-Node"))
                .body("dependencies[0].status", equalTo("UP"))
                .body("dependencies[1].name", equalTo("hazelcast"))
                .body("dependencies[1].status", equalTo("UP"));
    }
}
