package ee.ria.eidas.client.webapp;

import io.restassured.http.ContentType;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.Criterion;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.impl.KeyStoreCredentialResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;

import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.IsEqual.equalTo;

@RunWith(SpringRunner.class)
@SpringBootTest(
        properties= "spring.main.allow-bean-definition-overriding=true",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EidasClientApplicationHazelcastDisabledTest extends EidasClientApplicationTest {

    @TestConfiguration
    public static class TestConfWithEidasNodeSigningKey {

        @Autowired
        KeyStore samlKeystore;

        @Bean
        public Credential eidasNodeSigningCredential() throws ResolverException {
            Map<String, String> passwordMap = new HashMap<>();
            passwordMap.put("stork", "changeit");
            KeyStoreCredentialResolver resolver = new KeyStoreCredentialResolver(samlKeystore, passwordMap);

            Criterion criterion = new EntityIdCriterion("stork");
            CriteriaSet criteriaSet = new CriteriaSet();
            criteriaSet.add(criterion);

            return resolver.resolveSingle(criteriaSet);
        }
    }

    @Test
    public void heartbeat_shouldSucceed_whenEidasNodeRespondsOk() {
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
            .body("dependencies", hasSize(1))
            .body("dependencies[0].status", equalTo("UP"))
            .body("dependencies[0].name", equalTo("eIDAS-Node"));
    }

    @Test
    public void hazelcast_shouldNotBeAvailableByDefault() {
        given()
                .port(port)
        .when()
                .get("/hazelcast")
        .then()
                .statusCode(404);
    }

}
