package ee.ria.eidas.client.webapp;

import com.jayway.restassured.http.ContentType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static com.jayway.restassured.RestAssured.given;
import static ee.ria.eidas.client.session.HazelcastRequestSessionServiceImpl.UNANSWERED_REQUESTS_MAP;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.IsEqual.equalTo;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application-hazelcast-enabled.properties")
public class EidasClientApplicationHazelcastEnabledTest extends EidasClientApplicationTest {

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
                .body("maps[0].mapName", equalTo(UNANSWERED_REQUESTS_MAP))
                .body("maps[0].maxCapacity", notNullValue())
                .body("maps[0].creationTime", notNullValue())
                .body("maps[0].ownedEntryCount", notNullValue())
                .body("maps[0].backupEntryCount", notNullValue())
                .body("maps[0].backupCount", notNullValue())
                .body("maps[0].hitsCount", notNullValue())
                .body("maps[0].lastUpdateTime", notNullValue())
                .body("maps[0].lastAccessTime", notNullValue())
                .body("maps[0].lockedEntryCount", notNullValue())
                .body("maps[0].dirtyEntryCount", notNullValue())
                .body("maps[0].totalGetLatency", notNullValue())
                .body("maps[0].totalPutLatency", notNullValue())
                .body("maps[0].totalRemoveLatency", notNullValue())
                .body("maps[0].heapCost", notNullValue());
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
