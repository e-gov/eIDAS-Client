package ee.ria.eidas.client.webapp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EidasClientApplicationTest {

    @LocalServerPort
    int port;

    @Test
    public void testValidResponse() {
        given()
            .port(port)
        .when()
            .get("/")
        .then()
            .statusCode(200)
            .body("html.body", contains("Hello world!"));
    }

}
