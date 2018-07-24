package ee.ria.eidas.client.webapp;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.XmlConfig;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.ResponseBodyExtractionOptions;
import ee.ria.eidas.client.AuthInitiationService;
import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.EidasAttribute;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.fixtures.ResponseBuilder;
import ee.ria.eidas.client.session.RequestSession;
import ee.ria.eidas.client.session.RequestSessionService;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import ee.ria.eidas.client.utils.XmlUtils;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.Criterion;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.joda.time.DateTime;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.impl.AttributeStatementBuilder;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.impl.KeyStoreCredentialResolver;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.matcher.RestAssuredMatchers.matchesXsd;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EidasClientApplicationTest {

    private final static WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(7771));

    @LocalServerPort
    int port;

    @Autowired
    Credential metadataSigningCredential;

    @SpyBean(name = "responseAssertionDecryptionCredential")
    Credential responseAssertionDecryptionCredential;

    @Autowired
    Credential eidasNodeSigningCredential;

    @Autowired
    RequestSessionService requestSessionService;

    @Autowired
    EidasClientProperties eidasClientProperties;

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

    @Before
    public void removeDefaultRequestIdFromSessionStore() {
        requestSessionService.getAndRemoveRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO);
    }

    @Test
    public void metadataMatchesSchema() throws Exception {
        given()
            .port(port).config(RestAssured.config().xmlConfig(XmlConfig.xmlConfig().disableLoadingOfExternalDtd()))
        .when()
            .get("/metadata")
        .then()
            .statusCode(200)
            .body(matchesXsd(getClass().getClassLoader().getResource("schema/saml-schema-metadata-2.0.xsd").openStream()));
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

        EntityDescriptor signableObj = XmlUtils.unmarshallElement(body.asString());
        SignatureValidator.validate(signableObj.getSignature(), metadataSigningCredential);
    }

    @Test
    public void httpPostBinding_shouldPass_whenAllParamsPresent() {
        given()
            .port(port)
            .queryParam("Country", "EE")
            .queryParam("LoA", "LOW")
            .queryParam("RelayState", "test")
            .queryParam("AdditionalAttributes", "BirthName PlaceOfBirth CurrentAddress Gender LegalPersonIdentifier LegalName LegalAddress VATRegistration TaxReference LEI EORI SEED SIC D-2012-17-EUIdentifier")
        .when()
            .get("/login")
        .then()
            .statusCode(200)
            .body("html.body.form.@action", equalTo("http://localhost:8080/EidasNode/ServiceProvider"))
            .body("html.body.form.div.input[0].@value", equalTo("test"))
            .body("html.body.form.div.input[1].@value", not(empty()))
            .body("html.body.form.div.input[2].@value", equalTo("EE"));
    }

    @Test
    public void httpPostBinding_shouldFail_whenAdditionalParamsContainsSingleValidAttribute() {
        given()
            .port(port)
            .queryParam("Country", "EE")
            .queryParam("LoA", "LOW")
            .queryParam("RelayState", "test")
            .queryParam("AdditionalAttributes", "LegalPersonIdentifier")
        .when()
            .get("/login")
        .then()
            .statusCode(200)
            .body("html.body.form.@action", equalTo("http://localhost:8080/EidasNode/ServiceProvider"))
            .body("html.body.form.div.input[0].@value", equalTo("test"))
            .body("html.body.form.div.input[1].@value", not(empty()))
            .body("html.body.form.div.input[2].@value", equalTo("EE"));
    }

    @Test
    public void httpPostBinding_shouldFail_whenAdditionalParamsContainsInvalidAttribute() {
        given()
            .port(port)
            .queryParam("Country", "EE")
            .queryParam("LoA", "LOW")
            .queryParam("RelayState", "test")
            .queryParam("AdditionalAttributes", "LegalPersonIdentifier XXXX LegalName")
        .when()
            .get("/login")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo("Invalid SAMLResponse. Found one or more invalid AdditionalAttributes value(s). Allowed values are: [BirthName, PlaceOfBirth, CurrentAddress, Gender, LegalPersonIdentifier, LegalName, LegalAddress, VATRegistration, TaxReference, LEI, EORI, SEED, SIC, D-2012-17-EUIdentifier]"));
    }

    @Test
    public void httpPostBinding_shouldFail_whenLoAContainsInvalidValue() {
        given()
            .port(port)
            .queryParam("Country", "EE")
            .queryParam("LoA", "ABCdef")
        .when()
            .get("/login")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo("Invalid value for parameter LoA"));
    }

    @Test
    public void httpPostBinding_shouldPass_whenOnlyCountryParamPresent() {
        given()
            .port(port)
            .queryParam("Country", "EE")
        .when()
            .get("/login")
        .then()
            .statusCode(200)
            .body("html.body.form.@action", equalTo("http://localhost:8080/EidasNode/ServiceProvider"))
            .body("html.body.form.div.input[0].@value", not(empty()))
            .body("html.body.form.div.input[1].@value", equalTo("EE"));
    }

    @Test
    public void httpPostBinding_shouldFail_whenCountryRequestParamInvalid() {
        given()
            .port(port)
            .queryParam("Country", "NEVERLAND")
            .queryParam("RelayState", "test")
        .when()
            .get("/login")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo("Invalid SAMLResponse. Invalid country! Valid countries:[EE, CA]"));
    }

    @Test
    public void httpPostBinding_shouldFail_whenRelayStateRequestParamInvalid() {
        given()
            .port(port)
            .queryParam("Country", "EE")
            .queryParam("RelayState", "ä")
        .when()
            .get("/login")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo("Invalid SAMLResponse. Invalid RelayState! Must match the following regexp: ^[a-zA-Z0-9-_]{0,80}$"));
    }

    @Test
    public void httpPostBinding_shouldFail_whenMissingMandatoryParameter() {
        given()
            .port(port)
        .when()
            .get("/login")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo("Required String parameter 'Country' is not present"));
    }

    @Test
    public void returnUrl_shouldSucceed_whenValidSAMLResponseWithNaturalPersonMinimalAttributeSet() {
        ResponseBuilder responseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        saveNewRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO, new DateTime(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);

        given()
            .port(port)
            .contentType("application/x-www-form-urlencoded")
            .formParam("RelayState", "some-state")
            .formParam("SAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(responseBuilder
                    .buildResponse("http://localhost:7771/EidasNode/ConnectorResponderMetadata")).getBytes(StandardCharsets.UTF_8)))
        .when()
            .post("/returnUrl")
        .then()
            .statusCode(200)
            .body("levelOfAssurance", equalTo("http://eidas.europa.eu/LoA/low"))
            .body("attributes.PersonIdentifier", equalTo("CA/CA/12345"))
            .body("attributes.FamilyName", equalTo("Ωνάσης"))
            .body("attributes.FirstName", equalTo("Αλέξανδρος"))
            .body("attributesTransliterated.FamilyName", equalTo("Onassis"))
            .body("attributesTransliterated.FirstName", equalTo("Alexander"));
    }

    @Test
    public void returnUrl_shouldSucceed_whenValidSAMLResponseWithAllAttributesPresent() {
        ResponseBuilder responseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        saveNewRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO, new DateTime(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);
        AttributeStatement attributeStatement = new AttributeStatementBuilder().buildObject();

        attributeStatement.getAttributes().add(responseBuilder.buildAttribute("FirstName", "http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri", "eidas-natural:CurrentGivenNameType", "Alexander", "Αλέξανδρος"));
        attributeStatement.getAttributes().add(responseBuilder.buildAttribute("FamilyName", "http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri", "eidas-natural:CurrentFamilyNameType", "Onassis", "Ωνάσης"));
        attributeStatement.getAttributes().add(responseBuilder.buildAttribute("PersonIdentifier", "http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri", "eidas-natural:PersonIdentifierType", "CA/CA/12345"));
        attributeStatement.getAttributes().add(responseBuilder.buildAttribute("DateOfBirth", "http://eidas.europa.eu/attributes/naturalperson/DateOfBirth", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri", "eidas-natural:DateOfBirthType", "1965-01-01"));

        attributeStatement.getAttributes().add(responseBuilder.buildAttribute("UnknownMsSpecificAttribute", "http://eidas.europa.eu/attributes/ms/specific/Unknown", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri", "eidas-natural:Custom", "Unspecified"));

        Response response = responseBuilder.buildResponse("http://localhost:7771/EidasNode/ConnectorResponderMetadata",
                Collections.singletonMap(ResponseBuilder.InputType.ATTRIBUTE_STATEMENT, Optional.of(attributeStatement)));

        given()
                .port(port)
                .contentType("application/x-www-form-urlencoded")
                .formParam("RelayState", "some-state")
                .formParam("SAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(response).getBytes(StandardCharsets.UTF_8)))
        .when()
                .post("/returnUrl")
        .then()
                .statusCode(200)
                .body("levelOfAssurance", equalTo("http://eidas.europa.eu/LoA/low"))
                .body("attributes.PersonIdentifier", equalTo("CA/CA/12345"))
                .body("attributes.FamilyName", equalTo("Ωνάσης"))
                .body("attributes.FirstName", equalTo("Αλέξανδρος"))
                .body("attributes.FirstName", equalTo("Αλέξανδρος"))
                .body("attributes.UnknownMsSpecificAttribute", equalTo("Unspecified"))
                .body("attributesTransliterated.FamilyName", equalTo("Onassis"))
                .body("attributesTransliterated.FirstName", equalTo("Alexander"));
    }

    @Test
    public void returnUrl_shouldFail_whenAuthenticationFails() {
        ResponseBuilder responseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        Response response = responseBuilder.buildResponse("http://localhost:7771/EidasNode/ConnectorResponderMetadata",
                Collections.singletonMap(ResponseBuilder.InputType.STATUS, Optional.of(responseBuilder.buildAuthnFailedStatus())));

        given()
            .port(port)
            .contentType("application/x-www-form-urlencoded")
            .formParam("SAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(response).getBytes(StandardCharsets.UTF_8)))
        .when()
            .post("/returnUrl")
        .then()
            .statusCode(401)
            .body("error", equalTo("Unauthorized"))
            .body("message", equalTo("Authentication failed."));
    }

    @Test
    public void returnUrl_shouldFail_whenInvalidSchema() {
        given()
            .port(port)
            .contentType("application/x-www-form-urlencoded")
            .formParam("SAMLResponse", Base64.getEncoder().encodeToString("<saml2p:Response xmlns:saml2p=\"urn:oasis:names:tc:SAML:2.0:protocol\" xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\" xmlns:eidas=\"http://eidas.europa.eu/attributes/naturalperson\" xmlns:saml2=\"urn:oasis:names:tc:SAML:2.0:assertion\" Consent=\"urn:oasis:names:tc:SAML:2.0:consent:obtained\" Destination=\"https://eidastest.eesti.ee/SP/ReturnPage\" ID=\"_3mEzdFJfrtUjn2m2AiVlzcPWQMzUbKbSeBy361IbOJ5bgQsy.luTRPBp5amP1KG\" InResponseTo=\"_dfe8paUm3yG_u4-fdnCtUoM.mQjQnD874VyioAEB71q8waJkIQLBjOP5HdfGLwP\" IssueInstant=\"2018-03-22T12:03:26.306Z\" Version=\"2.0\"></saml2p:Response>".getBytes(StandardCharsets.UTF_8)))
        .when()
            .post("/returnUrl")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo("Invalid SAMLResponse. Error handling message: Message is not schema-valid."));
    }


    @Test
    public void returnUrl_shouldFail_whenNoUserConsentGiven() {
        ResponseBuilder responseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        Response response = responseBuilder.buildResponse("http://localhost:8080/EidasNode/ConnectorResponderMetadata",
                Collections.singletonMap(ResponseBuilder.InputType.STATUS, Optional.of(responseBuilder.buildRequesterRequestDeniedStatus())));

        given()
            .port(port)
            .contentType("application/x-www-form-urlencoded")
            .formParam("SAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(response).getBytes(StandardCharsets.UTF_8)))
        .when()
            .post("/returnUrl")
        .then()
            .statusCode(401)
            .body("error", equalTo("Unauthorized"))
            .body("message", equalTo("No user consent received. User denied access."));
    }

    @Test
    public void returnUrl_shouldFail_whenInternalError() {
        ResponseBuilder responseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        saveNewRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO, new DateTime(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);
        Mockito.when(responseAssertionDecryptionCredential.getPrivateKey()).thenThrow(new RuntimeException("Ooops! An internal error occurred!"));

        given()
            .port(port)
            .contentType("application/x-www-form-urlencoded")
            .formParam("relayState", "some-state")
            .formParam("SAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(responseBuilder.buildResponse("http://localhost:7771/EidasNode/ConnectorResponderMetadata")).getBytes(StandardCharsets.UTF_8)))
        .when()
            .post("/returnUrl")
        .then()
            .statusCode(500)
            .body("error", equalTo("Internal Server Error"))
            .body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));
    }

    @Test
    public void returnUrl_shouldFail_whenSAMLResponseParamMissing() {
        ResponseBuilder responseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        saveNewRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO, new DateTime(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);

        given()
            .port(port)
            .contentType("application/x-www-form-urlencoded")
            .formParam("relayState", "some-state")
            .formParam("notSAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(responseBuilder.buildResponse("http://localhost:7771/EidasNode/ConnectorResponderMetadata")).getBytes(StandardCharsets.UTF_8)))
        .when()
            .post("/returnUrl")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo("Required String parameter 'SAMLResponse' is not present"));
    }

    @Test
    public void returnUrl_shouldFail_whenSAMLResponseParamEmpty() {
        saveNewRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO, new DateTime(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);

        given()
            .port(port)
            .contentType("application/x-www-form-urlencoded")
            .formParam("relayState", "some-state")
            .formParam("SAMLResponse", "")
        .when()
            .post("/returnUrl")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo("Required String parameter 'SAMLResponse' is not present"));
    }

    @Test
    public void returnUrl_shouldFail_whenSAMLResponseIsNotSigned() {
        ResponseBuilder responseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        Response response = responseBuilder.buildResponse("http://localhost:7771/EidasNode/ConnectorResponderMetadata");
        response.setSignature(null);

        given()
            .port(port)
            .contentType("application/x-www-form-urlencoded")
            .formParam("SAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(response).getBytes(StandardCharsets.UTF_8)))
        .when()
            .post("/returnUrl")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo("Invalid SAMLResponse. Response not signed."));
    }

    @Test
    public void returnUrl_shouldFail_whenSAMLResponseSignatureDoesNotVerify() {
        ResponseBuilder responseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        Response response = responseBuilder.buildResponse("http://localhost:7771/EidasNode/ConnectorResponderMetadata");
        response.setInResponseTo("new-inResponseTo-to-invalidate-signature");

        given()
            .port(port)
            .contentType("application/x-www-form-urlencoded")
            .formParam("SAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(response).getBytes(StandardCharsets.UTF_8)))
        .when()
            .post("/returnUrl")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo("Invalid SAMLResponse. Invalid response signature."));
    }

    @Test
    public void returnUrl_shouldFail_whenNoRequestSessionPresent() {
        ResponseBuilder responseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        Response response = responseBuilder.buildResponse("http://localhost:7771/EidasNode/ConnectorResponderMetadata");

        given()
            .port(port)
            .contentType("application/x-www-form-urlencoded")
            .formParam("SAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(response).getBytes(StandardCharsets.UTF_8)))
        .when()
            .post("/returnUrl")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo("Invalid SAMLResponse. No corresponding SAML request session found for the given response!"));
    }

    @Test
    public void returnUrl_shouldFail_whenRequestSessionHasExpired() {
        ResponseBuilder responseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        DateTime issueInstant = new DateTime().minusSeconds(eidasClientProperties.getResponseMessageLifeTime()).minusSeconds(eidasClientProperties.getAcceptedClockSkew());
        saveNewRequestSession(responseBuilder.DEFAULT_IN_RESPONSE_TO, issueInstant, AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);
        Response response = responseBuilder.buildResponse("http://localhost:7771/EidasNode/ConnectorResponderMetadata");

        given()
            .port(port)
            .contentType("application/x-www-form-urlencoded")
            .formParam("SAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(response).getBytes(StandardCharsets.UTF_8)))
        .when()
            .post("/returnUrl")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo(String.format("Invalid SAMLResponse. Request session with ID %s has expired!", responseBuilder.DEFAULT_IN_RESPONSE_TO)));
    }

    @Test
    public void returnUrl_shouldFail_whenResponseIssueInstantHasExpired() {
        ResponseBuilder responseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        saveNewRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO, new DateTime(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);
        DateTime pastTime = new DateTime().minusSeconds(eidasClientProperties.getResponseMessageLifeTime()).minusSeconds(eidasClientProperties.getAcceptedClockSkew());
        Response response = responseBuilder.buildResponse("http://localhost:7771/EidasNode/ConnectorResponderMetadata",
                Collections.singletonMap(ResponseBuilder.InputType.ISSUE_INSTANT, Optional.of(pastTime)));

        given()
            .port(port)
            .contentType("application/x-www-form-urlencoded")
            .formParam("SAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(response).getBytes(StandardCharsets.UTF_8)))
        .when()
            .post("/returnUrl")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo("Invalid SAMLResponse. Error handling message: Message was rejected due to issue instant expiration"));
    }

    @Test
    public void returnUrl_shouldFail_whenResponseIssueInstantIsInTheFuture() {
        ResponseBuilder responseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        saveNewRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO, new DateTime(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);
        DateTime futureTime = new DateTime().plusSeconds(1).plusSeconds(eidasClientProperties.getResponseMessageLifeTime()).plusSeconds(eidasClientProperties.getAcceptedClockSkew());
        Response response = responseBuilder.buildResponse("http://localhost:7771/EidasNode/ConnectorResponderMetadata",
                Collections.singletonMap(ResponseBuilder.InputType.ISSUE_INSTANT, Optional.of(futureTime)));

        given()
            .port(port)
            .contentType("application/x-www-form-urlencoded")
            .formParam("SAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(response).getBytes(StandardCharsets.UTF_8)))
        .when()
            .post("/returnUrl")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo("Invalid SAMLResponse. Error handling message: Message was rejected because it was issued in the future"));
    }

    @Test
    public void returnUrl_shouldFail_whenResponseInResponseToIsInvalid() {
        ResponseBuilder responseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        saveNewRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO, new DateTime(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);
        Response response = responseBuilder.buildResponse("http://localhost:7771/EidasNode/ConnectorResponderMetadata",
                Collections.singletonMap(ResponseBuilder.InputType.IN_RESPONSE_TO, Optional.of("invalid-inResponseTo")));

        given()
            .port(port)
            .contentType("application/x-www-form-urlencoded")
            .formParam("SAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(response).getBytes(StandardCharsets.UTF_8)))
        .when()
            .post("/returnUrl")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo("Invalid SAMLResponse. No corresponding SAML request session found for the given response!"));
    }

    @Test
    public void returnUrl_shouldFail_whenAssertionInResponseToIsInvalid() {
        ResponseBuilder responseBuilder = new ResponseBuilder(eidasNodeSigningCredential, responseAssertionDecryptionCredential);
        saveNewRequestSession(ResponseBuilder.DEFAULT_IN_RESPONSE_TO, new DateTime(), AssuranceLevel.LOW, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);
        Response response = responseBuilder.buildResponse("http://localhost:7771/EidasNode/ConnectorResponderMetadata",
                Collections.singletonMap(ResponseBuilder.InputType.ASSERTION_IN_RESPONSE_TO, Optional.of("invalid-inResponseTo")));

        given()
            .port(port)
            .contentType("application/x-www-form-urlencoded")
            .formParam("SAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(response).getBytes(StandardCharsets.UTF_8)))
        .when()
            .post("/returnUrl")
        .then()
            .statusCode(400)
            .body("error", equalTo("Bad Request"))
            .body("message", equalTo("Invalid SAMLResponse. No corresponding SAML request session found for the given response assertion!"));
    }

    @Test
    public void returnUrl_shouldFail_whenInvalidMethod() {
        given()
            .port(port)
        .when()
            .put("/returnUrl")
        .then()
            .statusCode(405)
            .body("error", equalTo("Method Not Allowed"))
            .body("message", equalTo("Request method 'PUT' not supported"));
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
            .body("dependencies[0].status", equalTo("UP"));
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

    @AfterClass
    public static void teardown() throws Exception {
        wireMockServer.stop();
    }

    private void saveNewRequestSession(String requestID, DateTime issueInstant, AssuranceLevel loa, List<EidasAttribute> requestedAttributes) {
        RequestSession requestSession = new RequestSession(requestID, issueInstant, loa, requestedAttributes);
        requestSessionService.saveRequestSession(requestID, requestSession);
    }

}
