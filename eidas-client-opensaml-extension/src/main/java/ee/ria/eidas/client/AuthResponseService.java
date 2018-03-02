package ee.ria.eidas.client;

import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.config.OpenSAMLConfiguration;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.response.AuthenticationResult;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import net.shibboleth.utilities.java.support.xml.XMLParserException;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.handler.MessageHandler;
import org.opensaml.messaging.handler.MessageHandlerException;
import org.opensaml.messaging.handler.impl.BasicMessageHandlerChain;
import org.opensaml.saml.common.binding.security.impl.MessageLifetimeSecurityHandler;
import org.opensaml.saml.common.binding.security.impl.ReceivedEndpointSecurityHandler;
import org.opensaml.saml.common.messaging.context.SAMLMessageInfoContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.criterion.EntityRoleCriterion;
import org.opensaml.saml.criterion.ProtocolCriterion;
import org.opensaml.saml.saml2.core.*;
import org.opensaml.saml.saml2.encryption.Decrypter;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.UsageType;
import org.opensaml.security.criteria.UsageCriterion;
import org.opensaml.xmlsec.encryption.support.DecryptionException;
import org.opensaml.xmlsec.encryption.support.InlineEncryptedKeyResolver;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class AuthResponseService {

    private static Logger LOGGER = LoggerFactory.getLogger(AuthResponseService.class);

    private EidasClientProperties eidasClientProperties;

    private ExplicitKeySignatureTrustEngine explicitKeySignatureTrustEngine;

    private Credential spAssertionDecryptionCredential;

    public AuthResponseService(EidasClientProperties eidasClientProperties, ExplicitKeySignatureTrustEngine explicitKeySignatureTrustEngine, Credential spAssertionDecryptionCredential) {
        this.eidasClientProperties = eidasClientProperties;
        this.explicitKeySignatureTrustEngine = explicitKeySignatureTrustEngine;
        this.spAssertionDecryptionCredential = spAssertionDecryptionCredential;
    }

    public AuthenticationResult getAuthenticationResult(HttpServletRequest req) {
        Response samlResponse;
        try {
            String encodedSamlResponse = req.getParameter("SAMLResponse");
            byte[] decodedSamlResponse = Base64.getDecoder().decode(encodedSamlResponse);
            String decodedSAMLstr = new String(decodedSamlResponse, StandardCharsets.UTF_8);
            samlResponse = getSamlResponse(decodedSAMLstr);
            LOGGER.info(OpenSAMLUtils.getXmlString(samlResponse));
        } catch (Exception e) {
            throw new EidasClientException("Failed to read SAMLResponse. " + e.getMessage(), e);
        }

        validateDestinationAndLifetime(samlResponse, req);

        EncryptedAssertion encryptedAssertion = getEncryptedAssertion(samlResponse);
        Assertion assertion = decryptAssertion(encryptedAssertion);
        verifyAssertionSignature(assertion);
        LOGGER.info("Decrypted Assertion: ");
        LOGGER.info(OpenSAMLUtils.getXmlString(assertion));

        logAssertionAttributes(assertion);
        logAuthenticationInstant(assertion);
        logAuthenticationMethod(assertion);

        return new AuthenticationResult(samlResponse, assertion);
    }

    private Response getSamlResponse(String samlResponse) throws XMLParserException, UnmarshallingException{
        return (Response) XMLObjectSupport.unmarshallFromInputStream(
                OpenSAMLConfiguration.getParserPool(), new ByteArrayInputStream(samlResponse.getBytes(StandardCharsets.UTF_8)));
    }

    private void validateDestinationAndLifetime(Response samlResponse, HttpServletRequest request) {
        MessageContext context = new MessageContext<Response>();
        context.setMessage(samlResponse);

        SAMLMessageInfoContext messageInfoContext = context.getSubcontext(SAMLMessageInfoContext.class, true);
        messageInfoContext.setMessageIssueInstant(samlResponse.getIssueInstant());

        MessageLifetimeSecurityHandler lifetimeSecurityHandler = new MessageLifetimeSecurityHandler();
        lifetimeSecurityHandler.setClockSkew(1000);
        lifetimeSecurityHandler.setMessageLifetime(2000);
        lifetimeSecurityHandler.setRequiredRule(true);

        ReceivedEndpointSecurityHandler receivedEndpointSecurityHandler = new ReceivedEndpointSecurityHandler();
        receivedEndpointSecurityHandler.setHttpServletRequest(request);
        List handlers = new ArrayList<MessageHandler>();
        handlers.add(lifetimeSecurityHandler);
        handlers.add(receivedEndpointSecurityHandler);

        BasicMessageHandlerChain<ArtifactResponse> handlerChain = new BasicMessageHandlerChain<ArtifactResponse>();
        handlerChain.setHandlers(handlers);

        try {
            handlerChain.initialize();
            handlerChain.doInvoke(context);
        } catch (ComponentInitializationException e) {
            throw new EidasClientException("Error initializing handler chain", e);
        } catch (MessageHandlerException e) {
            throw new EidasClientException("Error handling message", e);
        }

    }

    private Assertion decryptAssertion(EncryptedAssertion encryptedAssertion) {
        StaticKeyInfoCredentialResolver keyInfoCredentialResolver = new StaticKeyInfoCredentialResolver(spAssertionDecryptionCredential);

        Decrypter decrypter = new Decrypter(null, keyInfoCredentialResolver, new InlineEncryptedKeyResolver());
        decrypter.setRootInNewDocument(true);

        try {
            return decrypter.decrypt(encryptedAssertion);
        } catch (DecryptionException e) {
            throw new RuntimeException(e);
        }
    }

    private void verifyAssertionSignature(Assertion assertion) {
        if (!assertion.isSigned()) {
            throw new RuntimeException("The SAML Assertion was not signed");
        }
        try {
            SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
            profileValidator.validate(assertion.getSignature());

            final CriteriaSet criteriaSet = new CriteriaSet();
            criteriaSet.add(new UsageCriterion(UsageType.SIGNING));
            criteriaSet.add(new EntityRoleCriterion(IDPSSODescriptor.DEFAULT_ELEMENT_NAME));
            criteriaSet.add(new ProtocolCriterion(SAMLConstants.SAML20P_NS));
            criteriaSet.add(new EntityIdCriterion(eidasClientProperties.getIdpMetadataUrl()));
            Credential credential = explicitKeySignatureTrustEngine.getCredentialResolver().resolveSingle(criteriaSet);
            SignatureValidator.validate(assertion.getSignature(), credential);

            LOGGER.info("SAML Assertion signature verified");
        } catch (SignatureException|ResolverException e) {
            throw new IllegalStateException("Signature verification failed!", e);
        }

    }

    private void logAuthenticationMethod(Assertion assertion) {
        LOGGER.info("Authentication method: " + assertion.getAuthnStatements().get(0)
                .getAuthnContext().getAuthnContextClassRef().getAuthnContextClassRef());
    }

    private void logAuthenticationInstant(Assertion assertion) {
        LOGGER.info("Authentication instant: " + assertion.getAuthnStatements().get(0).getAuthnInstant());
    }

    private void logAssertionAttributes(Assertion assertion) {
        for (Attribute attribute : assertion.getAttributeStatements().get(0).getAttributes()) {
            LOGGER.info("Attribute name: " + attribute.getName());
            for (XMLObject attributeValue : attribute.getAttributeValues()) {
                LOGGER.info("Attribute value: " + attributeValue.toString());
            }
        }
    }

    private EncryptedAssertion getEncryptedAssertion(Response samlResponse) {
        List<EncryptedAssertion> response = samlResponse.getEncryptedAssertions();
        if (response == null || response.isEmpty()) {
            throw new EidasClientException("Saml Response does not contain any encrypted assertions");
        }
        return response.get(0);
    }
}
