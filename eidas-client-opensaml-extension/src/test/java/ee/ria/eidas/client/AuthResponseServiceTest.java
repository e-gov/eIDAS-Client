package ee.ria.eidas.client;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.response.AuthenticationResult;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.Criterion;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.impl.XSAnyBuilder;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.saml2.core.*;
import org.opensaml.saml.saml2.core.impl.*;
import org.opensaml.saml.saml2.encryption.Encrypter;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.impl.KeyStoreCredentialResolver;
import org.opensaml.xmlsec.encryption.support.DataEncryptionParameters;
import org.opensaml.xmlsec.encryption.support.EncryptionConstants;
import org.opensaml.xmlsec.encryption.support.KeyEncryptionParameters;
import org.opensaml.xmlsec.keyinfo.KeyInfoGenerator;
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.Signer;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.xml.namespace.QName;
import java.security.KeyStore;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = EidasClientConfiguration.class)
@TestPropertySource(locations = "classpath:application-test.properties")
public class AuthResponseServiceTest {

    @Autowired
    private EidasClientProperties properties;

    @Autowired
    private ExplicitKeySignatureTrustEngine responseSignatureTrustEngine;

    @Autowired
    private Credential responseAssertionDecryptionCredential;

    @Autowired
    public KeyStore samlKeystore;

    AuthResponseService authResponseService;

    private MockHttpServletRequest httpRequest;

    @Before
    public void setUp() throws Exception {
        authResponseService = new AuthResponseService(properties, responseSignatureTrustEngine, responseAssertionDecryptionCredential);

        httpRequest = new MockHttpServletRequest();
        httpRequest.setParameter("SAMLResponse", Base64.getEncoder().encodeToString(OpenSAMLUtils.getXmlString(buildResponse()).getBytes()));
        httpRequest.setServerName("localhost");
        httpRequest.setServerPort(8889);
        httpRequest.setRequestURI("/returnUrl");
    }

    @Test
    public void whenResponseValidatesSuccessfully_AuthenticationResultIsReturned() {
        AuthenticationResult result = authResponseService.getAuthenticationResult(httpRequest);
        assertAuthenticationResult(result);
    }

    private Response buildResponse() throws Exception {
        Credential credential = getCredential("stork", "changeit");

        Signature signature = (Signature) XMLObjectProviderRegistrySupport.getBuilderFactory()
                .getBuilder(Signature.DEFAULT_ELEMENT_NAME).buildObject(Signature.DEFAULT_ELEMENT_NAME);
        signature.setSigningCredential(credential);
        signature.setSignatureAlgorithm(getSignatureAlgorithm(credential));
        signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

        X509KeyInfoGeneratorFactory x509KeyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
        x509KeyInfoGeneratorFactory.setEmitEntityCertificate(true);
        KeyInfo keyInfo = x509KeyInfoGeneratorFactory.newInstance().generate(credential);
        signature.setKeyInfo(keyInfo);

        DateTime now = new DateTime();

        Response authnResponse = OpenSAMLUtils.buildSAMLObject(Response.class);
        authnResponse.setIssueInstant(now);
        authnResponse.setDestination("http://localhost:8889/returnUrl");
        authnResponse.setInResponseTo("sqajsja");
        authnResponse.setVersion(SAMLVersion.VERSION_20);
        authnResponse.setID(OpenSAMLUtils.generateSecureRandomId());
        authnResponse.setSignature(signature);
        authnResponse.setStatus(buildSuccessStatus());
        authnResponse.getEncryptedAssertions().add(buildAssertion(now));

        XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(authnResponse).marshall(authnResponse);
        Signer.signObject(signature);

        return authnResponse;
    }

    private String getSignatureAlgorithm(Credential credential) {
        if ("RSA".equals(credential.getPublicKey().getAlgorithm())) {
            return SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256;
        } else {
            return SignatureConstants.ALGO_ID_SIGNATURE_ECDSA_SHA256;
        }
    }

    private Status buildSuccessStatus() {
        Status status = new StatusBuilder().buildObject();
        StatusCode statusCode = new StatusCodeBuilder().buildObject();
        statusCode.setValue("urn:oasis:names:tc:SAML:2.0:status:Success");
        status.setStatusCode(statusCode);
        StatusMessage statusMessage = new StatusMessageBuilder().buildObject();
        statusMessage.setMessage("urn:oasis:names:tc:SAML:2.0:status:Success");
        status.setStatusMessage(statusMessage);
        return status;
    }

    private EncryptedAssertion buildAssertion(DateTime issueInstant) throws Exception {
        Credential credential = getCredential("stork", "changeit");

        Signature signature = (Signature) XMLObjectProviderRegistrySupport.getBuilderFactory()
                .getBuilder(Signature.DEFAULT_ELEMENT_NAME).buildObject(Signature.DEFAULT_ELEMENT_NAME);
        signature.setSigningCredential(credential);
        signature.setSignatureAlgorithm(getSignatureAlgorithm(credential));
        signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

        X509KeyInfoGeneratorFactory x509KeyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
        x509KeyInfoGeneratorFactory.setEmitEntityCertificate(true);
        KeyInfo keyInfo = x509KeyInfoGeneratorFactory.newInstance().generate(credential);
        signature.setKeyInfo(keyInfo);

        Assertion assertion = new AssertionBuilder().buildObject();
        assertion.setIssueInstant(issueInstant);
        assertion.setID(OpenSAMLUtils.generateSecureRandomId());
        assertion.setVersion(SAMLVersion.VERSION_20);
        assertion.setIssuer(buildIssuer());
        assertion.setSubject(buildSubject(issueInstant));
        assertion.setConditions(buildConditions(issueInstant));
        assertion.getAuthnStatements().add(buildAuthnStatement(issueInstant));
        assertion.getAttributeStatements().add(buildAttributeStatement());
        assertion.setSignature(signature);

        XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(assertion).marshall(assertion);
        Signer.signObject(signature);


        KeyEncryptionParameters kekParams = new KeyEncryptionParameters();
        kekParams.setEncryptionCredential(responseAssertionDecryptionCredential);
        kekParams.setAlgorithm(EncryptionConstants.ALGO_ID_KEYTRANSPORT_RSAOAEP);
        X509KeyInfoGeneratorFactory keyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
        keyInfoGeneratorFactory.setEmitEntityCertificate(true);
        KeyInfoGenerator keyInfoGenerator = keyInfoGeneratorFactory.newInstance();
        kekParams.setKeyInfoGenerator(keyInfoGenerator);

        DataEncryptionParameters encryptParams = new DataEncryptionParameters();
        encryptParams.setAlgorithm(EncryptionConstants.ALGO_ID_BLOCKCIPHER_AES128);

        Encrypter samlEncrypter = new Encrypter(encryptParams, kekParams);
        samlEncrypter.setKeyPlacement(Encrypter.KeyPlacement.INLINE);
        EncryptedAssertion encryptedAssertion = samlEncrypter.encrypt(assertion);
        return encryptedAssertion;
    }

    private Issuer buildIssuer() {
        Issuer issuer = new IssuerBuilder().buildObject();
        issuer.setFormat("urn:oasis:names:tc:SAML:2.0:nameid-format:entity");
        issuer.setValue("http://localhost:8080/EidasNode/ConnectorResponderMetadata");
        return issuer;
    }

    private Subject buildSubject(DateTime issueIstant) {
        Subject subject = new SubjectBuilder().buildObject();

        NameID nameID = new NameIDBuilder().buildObject();
        nameID.setFormat("urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified");
        nameID.setNameQualifier("http://C-PEPS.gov.xx");
        nameID.setValue("CA/CA/12345");
        subject.setNameID(nameID);

        SubjectConfirmation subjectConfirmation = new SubjectConfirmationBuilder().buildObject();
        subjectConfirmation.setMethod("urn:oasis:names:tc:SAML:2.0:cm:bearer");

        SubjectConfirmationData subjectConfirmationData = new SubjectConfirmationDataBuilder().buildObject();
        subjectConfirmationData.setAddress("172.24.0.1");
        subjectConfirmationData.setInResponseTo("_4ededd23fb88e6964df71b8bdb1c706f");
        subjectConfirmationData.setNotOnOrAfter(issueIstant.plusMinutes(5));
        subjectConfirmationData.setRecipient("http://192.168.82.40:8889/returnUrl");

        subjectConfirmation.setSubjectConfirmationData(subjectConfirmationData);
        subject.getSubjectConfirmations().add(subjectConfirmation);
        return subject;
    }

    private Conditions buildConditions(DateTime issueInstant) {
        Conditions conditions = new ConditionsBuilder().buildObject();
        conditions.setNotBefore(issueInstant);
        conditions.setNotOnOrAfter(issueInstant.plusMinutes(5));

        AudienceRestriction audienceRestriction = new AudienceRestrictionBuilder().buildObject();

        Audience audience = new AudienceBuilder().buildObject();
        audience.setAudienceURI("http://192.168.82.40:8889/metadata");

        audienceRestriction.getAudiences().add(audience);
        conditions.getAudienceRestrictions().add(audienceRestriction);
        return conditions;
    }

    private AuthnStatement buildAuthnStatement(DateTime issueInstant) {
        AuthnStatement authnStatement = new AuthnStatementBuilder().buildObject();
        authnStatement.setAuthnInstant(issueInstant.minusMinutes(1));

        AuthnContext authnContext = new AuthnContextBuilder().buildObject();

        AuthnContextClassRef authnContextClassRef = new AuthnContextClassRefBuilder().buildObject();
        authnContextClassRef.setAuthnContextClassRef(AssuranceLevel.LOW.getUri());
        authnContext.setAuthnContextClassRef(authnContextClassRef);

        AuthnContextDecl authnContextDecl = new AuthnContextDeclBuilder().buildObject();
        authnContext.setAuthnContextDecl(authnContextDecl);

        authnStatement.setAuthnContext(authnContext);
        return authnStatement;
    }

    private AttributeStatement buildAttributeStatement() {
        AttributeStatement attributeStatement = new AttributeStatementBuilder().buildObject();
        attributeStatement.getAttributes().add(buildAttribute("FirstName", "http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri", "eidas-natural:CurrentGivenNameType", "javier"));
        attributeStatement.getAttributes().add(buildAttribute("FamilyName", "http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri", "eidas-natural:CurrentFamilyNameType", "Garcia"));
        attributeStatement.getAttributes().add(buildAttribute("PersonIdendifier", "http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri", "eidas-natural:PersonIdentifierType", "CA/CA/12345"));
        attributeStatement.getAttributes().add(buildAttribute("DateOfBirth", "http://eidas.europa.eu/attributes/naturalperson/DateOfBirth", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri", "eidas-natural:DateOfBirthType", "1965-01-01"));
        return attributeStatement;
    }

    private Attribute buildAttribute(String friendlyName, String name, String nameFormat, String xsiType, String value) {
        Attribute attribute = new AttributeBuilder().buildObject();
        attribute.setFriendlyName(friendlyName);
        attribute.setName(name);
        attribute.setNameFormat(nameFormat);
        attribute.getAttributeValues().add(buildAttributeValue(xsiType, value));
        return attribute;
    }

    private XSAny buildAttributeValue(String xsiType, String value) {
        XSAny attributevalue = new XSAnyBuilder().buildObject(AttributeValue.DEFAULT_ELEMENT_NAME);
        attributevalue.getUnknownAttributes().put(new QName("http://www.w3.org/2001/XMLSchema-instance", "type", "xsi"), xsiType);
        attributevalue.setTextContent(value);
        return attributevalue;
    }

    private Credential getCredential(String keyPairId, String privateKeyPass) throws ResolverException {
        Map<String, String> passwordMap = new HashMap<>();
        passwordMap.put(keyPairId, privateKeyPass);
        KeyStoreCredentialResolver resolver = new KeyStoreCredentialResolver(samlKeystore, passwordMap);

        Criterion criterion = new EntityIdCriterion(keyPairId);
        CriteriaSet criteriaSet = new CriteriaSet();
        criteriaSet.add(criterion);

        return resolver.resolveSingle(criteriaSet);
    }

    private void assertAuthenticationResult(AuthenticationResult result) {
        assertEquals(StatusCode.SUCCESS, result.getStatusCode());
        assertEquals(AssuranceLevel.LOW.getUri(), result.getLevelOfAssurance());
        assertEquals("javier", result.getAttributes().get("FirstName"));
        assertEquals("Garcia", result.getAttributes().get("FamilyName"));
        assertEquals("CA/CA/12345", result.getAttributes().get("PersonIdendifier"));
        assertEquals("1965-01-01", result.getAttributes().get("DateOfBirth"));
    }

}