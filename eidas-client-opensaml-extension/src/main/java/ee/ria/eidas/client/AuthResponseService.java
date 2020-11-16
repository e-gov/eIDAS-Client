package ee.ria.eidas.client;

import ee.ria.eidas.client.metadata.IDPMetadataResolver;
import ee.ria.eidas.client.response.AssertionValidator;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.config.OpenSAMLConfiguration;
import ee.ria.eidas.client.exception.AuthenticationFailedException;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.exception.InvalidRequestException;
import ee.ria.eidas.client.response.AuthenticationResult;
import ee.ria.eidas.client.session.RequestSessionService;
import ee.ria.eidas.client.session.RequestSession;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.net.URIComparator;
import net.shibboleth.utilities.java.support.net.URIException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;

import org.joda.time.DateTime;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.handler.MessageHandler;
import org.opensaml.messaging.handler.MessageHandlerException;
import org.opensaml.messaging.handler.impl.BasicMessageHandlerChain;
import org.opensaml.messaging.handler.impl.SchemaValidateXMLMessage;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MissingServletRequestParameterException;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.xml.validation.Schema;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class AuthResponseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthResponseService.class);

    private RequestSessionService requestSessionService;

    private EidasClientProperties eidasClientProperties;

    private IDPMetadataResolver idpMetadataResolver;

    private Credential spAssertionDecryptionCredential;

    private Schema samlSchema;

    public AuthResponseService(RequestSessionService requestSessionService, EidasClientProperties eidasClientProperties, IDPMetadataResolver idpMetadataResolver, Credential spAssertionDecryptionCredential, Schema samlSchema) {
        this.requestSessionService = requestSessionService;
        this.eidasClientProperties = eidasClientProperties;
        this.idpMetadataResolver = idpMetadataResolver;
        this.spAssertionDecryptionCredential = spAssertionDecryptionCredential;
        this.samlSchema = samlSchema;
    }

    public AuthenticationResult getAuthenticationResult(HttpServletRequest req) throws MissingServletRequestParameterException {
        try {
            Response samlResponse = getSamlResponse(req);

            LOGGER.info("AuthnResponse ID: {}", samlResponse.getID());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("AuthnResponse: {}", OpenSAMLUtils.getXmlString(samlResponse));

            }

            validateDestinationAndLifetime(samlResponse, req);
            verifyResponseSignature(samlResponse);
            validateStatusCode(samlResponse);

            RequestSession requestSession = getAndValidateRequestSession(samlResponse);

            EncryptedAssertion encryptedAssertion = getEncryptedAssertion(samlResponse);
            Assertion assertion = decryptAssertion(encryptedAssertion);
            verifyAssertionSignature(assertion);
            validateAssertion(assertion, requestSession);

            LOGGER.info("Decrypted Assertion ID: {}", assertion.getID());

            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Decrypted Assertion: {}", OpenSAMLUtils.getXmlString(assertion));

            return new AuthenticationResult(assertion);
        } catch (InvalidRequestException exception) {
            throw new InvalidRequestException("Invalid SAMLResponse. " + exception.getMessage(), exception);
        }
    }

    private Response getSamlResponse(HttpServletRequest request) throws MissingServletRequestParameterException {
        String encodedSamlResponse = null;

        try {
            encodedSamlResponse = request.getParameter("SAMLResponse");
            if (StringUtils.isEmpty(encodedSamlResponse)) throw new IllegalArgumentException();
        } catch (Exception e) {
            throw new MissingServletRequestParameterException("SAMLResponse", "String");
        }

        try {
            byte[] decodedSamlResponse = Base64.getDecoder().decode(encodedSamlResponse);
            Response samlResponse = (Response) XMLObjectSupport.unmarshallFromInputStream(
                    OpenSAMLConfiguration.getParserPool(), new ByteArrayInputStream(decodedSamlResponse));

            LOGGER.info("SAML response ID: " + samlResponse.getID());
            if (LOGGER.isDebugEnabled())
                LOGGER.debug(OpenSAMLUtils.getXmlString(samlResponse));

            return samlResponse;
        } catch (Exception e) {
            throw new InvalidRequestException("Failed to read SAMLResponse. " + e.getMessage(), e);
        }
    }

    private void verifyResponseSignature(Response samlResponse) {
        if (!samlResponse.isSigned()) {
            throw new InvalidRequestException("Response not signed.");
        }
        try {
            samlResponse.getDOM().setIdAttribute("ID", true);

            SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
            profileValidator.validate(samlResponse.getSignature());

            final CriteriaSet criteriaSet = new CriteriaSet();
            criteriaSet.add(new UsageCriterion(UsageType.SIGNING));
            criteriaSet.add(new EntityRoleCriterion(IDPSSODescriptor.DEFAULT_ELEMENT_NAME));
            criteriaSet.add(new ProtocolCriterion(SAMLConstants.SAML20P_NS));
            criteriaSet.add(new EntityIdCriterion(eidasClientProperties.getIdpMetadataUrl()));
            Credential credential = idpMetadataResolver.responseSignatureTrustEngine().getCredentialResolver().resolveSingle(criteriaSet);
            SignatureValidator.validate(samlResponse.getSignature(), credential);

            LOGGER.debug("SAML Response signature verified");
        } catch (SignatureException | ResolverException e) {
            throw new InvalidRequestException("Invalid response signature.");
        }
    }

    private void validateStatusCode(Response samlResponse) {
        StatusCode statusCode = samlResponse.getStatus().getStatusCode();
        StatusCode substatusCode = statusCode.getStatusCode();
        StatusMessage statusMessage = samlResponse.getStatus().getStatusMessage();
        if (StatusCode.SUCCESS.equals(statusCode.getValue())) {
            LOGGER.info("AuthnResponse validation: {}", StatusCode.SUCCESS);
            return;
        }  else if (isStatusNoConsentGiven(statusCode, substatusCode, StatusCode.REQUESTER, StatusCode.REQUEST_DENIED)) {
            LOGGER.info("AuthnResponse validation: {}", StatusCode.REQUEST_DENIED);
            throw new AuthenticationFailedException("No user consent received. User denied access.");
        }  else if (isStatusAuthenticationFailed(statusCode, substatusCode, StatusCode.RESPONDER, StatusCode.AUTHN_FAILED)) {
            LOGGER.info("AuthnResponse validation: {}", StatusCode.AUTHN_FAILED);
            throw new AuthenticationFailedException("Authentication failed.");
        } else {
            LOGGER.info("AuthnResponse validation: FAILURE");
            throw new EidasClientException("Eidas node responded with an error! statusCode = " + samlResponse.getStatus().getStatusCode().getValue()
                    + (substatusCode != null ? ", substatusCode = " + substatusCode.getValue() : "")
                    +  ", statusMessage = " + statusMessage.getMessage());
        }
    }

    private boolean isStatusAuthenticationFailed(StatusCode statusCode, StatusCode substatusCode, String responder, String authnFailed) {
        return responder.equals(statusCode.getValue())
                && (substatusCode != null && authnFailed.equals(substatusCode.getValue()));
    }

    private boolean isStatusNoConsentGiven(StatusCode statusCode, StatusCode substatusCode, String requester, String requestDenied) {
        return requester.equals(statusCode.getValue())
                && (substatusCode != null && requestDenied.equals(substatusCode.getValue()));
    }

    private void validateDestinationAndLifetime(Response samlResponse, HttpServletRequest request) {
        MessageContext context = new MessageContext<Response>();
        context.setMessage(samlResponse);
        SAMLMessageInfoContext messageInfoContext = context.getSubcontext(SAMLMessageInfoContext.class, true);
        messageInfoContext.setMessageIssueInstant(samlResponse.getIssueInstant());

        SchemaValidateXMLMessage schemaValidationFilter = new SchemaValidateXMLMessage(samlSchema);

        MessageLifetimeSecurityHandler lifetimeSecurityHandler = new MessageLifetimeSecurityHandler();
        lifetimeSecurityHandler.setClockSkew(eidasClientProperties.getAcceptedClockSkew() * 1000L);
        lifetimeSecurityHandler.setMessageLifetime(eidasClientProperties.getResponseMessageLifetime() * 1000L);
        lifetimeSecurityHandler.setRequiredRule(true);

        ReceivedEndpointSecurityHandler receivedEndpointSecurityHandler = new ReceivedEndpointSecurityHandler();
        receivedEndpointSecurityHandler.setHttpServletRequest(request);
        List handlers = new ArrayList<MessageHandler>();

        handlers.add(schemaValidationFilter);
        handlers.add(lifetimeSecurityHandler);
        handlers.add(receivedEndpointSecurityHandler);
        receivedEndpointSecurityHandler.setURIComparator(new URIComparator() {
            @Override
            public boolean compare(@Nullable String messageDestination, @Nullable String receiverEndpoint) throws URIException {
                return messageDestination!= null && receiverEndpoint != null && messageDestination.equals(eidasClientProperties.getCallbackUrl());
            }
        });

        BasicMessageHandlerChain<ArtifactResponse> handlerChain = new BasicMessageHandlerChain<>();
        handlerChain.setHandlers(handlers);

        try {
            handlerChain.initialize();
            handlerChain.doInvoke(context);
        } catch (ComponentInitializationException e) {
            throw new EidasClientException("Error initializing handler chain", e);
        } catch (MessageHandlerException e) {
            throw new InvalidRequestException("Error handling message: " + e.getMessage(), e);
        }

    }

    private RequestSession getAndValidateRequestSession(Response samlResponse) {
        String requestID = samlResponse.getInResponseTo();

        RequestSession requestSession = requestSessionService.getAndRemoveRequestSession(requestID);
        if (requestSession == null) {
            throw new InvalidRequestException("No corresponding SAML request session found for the given response!");
        } else if (!requestSession.getRequestId().equals(requestID)) {
            throw new EidasClientException("Request session ID mismatch!");
        } else {
            DateTime now = new DateTime(requestSession.getIssueInstant().getZone());
            int maxAuthenticationLifetime = eidasClientProperties.getMaximumAuthenticationLifetime();
            int acceptedClockSkew = eidasClientProperties.getAcceptedClockSkew();

            if (now.isAfter(requestSession.getIssueInstant().plusSeconds(maxAuthenticationLifetime).plusSeconds(acceptedClockSkew))) {
                throw new InvalidRequestException("Request session with ID " + requestID + " has expired!");
            }
        }
        return requestSession;
    }

    private void validateAssertion(Assertion assertion, RequestSession requestSession) {
        AssertionValidator assertionValidator = new AssertionValidator(eidasClientProperties);
        assertionValidator.validate(assertion, requestSession);
    }

    private Assertion decryptAssertion(EncryptedAssertion encryptedAssertion) {
        StaticKeyInfoCredentialResolver keyInfoCredentialResolver = new StaticKeyInfoCredentialResolver(spAssertionDecryptionCredential);

        Decrypter decrypter = new Decrypter(null, keyInfoCredentialResolver, new InlineEncryptedKeyResolver());
        decrypter.setRootInNewDocument(true);

        try {
            return decrypter.decrypt(encryptedAssertion);
        } catch (DecryptionException e) {
            throw new EidasClientException("Error decrypting assertion", e);
        }
    }

    private void verifyAssertionSignature(Assertion assertion) {
        if (!assertion.isSigned()) {
            throw new InvalidRequestException("The SAML Assertion was not signed");
        }
        try {
            SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
            profileValidator.validate(assertion.getSignature());

            final CriteriaSet criteriaSet = new CriteriaSet();
            criteriaSet.add(new UsageCriterion(UsageType.SIGNING));
            criteriaSet.add(new EntityRoleCriterion(IDPSSODescriptor.DEFAULT_ELEMENT_NAME));
            criteriaSet.add(new ProtocolCriterion(SAMLConstants.SAML20P_NS));
            criteriaSet.add(new EntityIdCriterion(eidasClientProperties.getIdpMetadataUrl()));
            Credential credential = idpMetadataResolver.responseSignatureTrustEngine().getCredentialResolver().resolveSingle(criteriaSet);
            SignatureValidator.validate(assertion.getSignature(), credential);

            LOGGER.debug("SAML Assertion signature verified");
        } catch (SignatureException | ResolverException e) {
            throw new EidasClientException("Signature verification failed!", e);
        }
    }

    private EncryptedAssertion getEncryptedAssertion(Response samlResponse) {
        List<EncryptedAssertion> response = samlResponse.getEncryptedAssertions();
        if (response == null || response.isEmpty()) {
            throw new EidasClientException("Saml Response does not contain any encrypted assertions");
        } else if (response.size() > 1) {
            throw new EidasClientException("Saml Response contains more than 1 encrypted assertion");
        }
        return response.get(0);
    }
}
