package ee.ria.eidas.client;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.AuthnRequestBuilder;
import ee.ria.eidas.client.authnrequest.EidasHTTPPostEncoder;
import ee.ria.eidas.client.authnrequest.RequestSessionService;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.exception.InvalidEidasParamException;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.SignatureSigningParameters;
import org.opensaml.xmlsec.context.SecurityParametersContext;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AuthInitiationService {

    private static Logger LOGGER = LoggerFactory.getLogger(AuthInitiationService.class);

    private static String RELAYSTATE_VALIDATION_REGEXP = "^[a-zA-Z0-9-_]{0,80}$";

    private RequestSessionService requestSessionService;

    private Credential authnReqSigningCredential;

    private EidasClientProperties eidasClientProperties;

    private SingleSignOnService singleSignOnService;

    public AuthInitiationService(RequestSessionService requestSessionService, Credential authnReqSigningCredential, EidasClientProperties eidasClientProperties, SingleSignOnService singleSignOnService) {
        this.requestSessionService = requestSessionService;
        this.authnReqSigningCredential = authnReqSigningCredential;
        this.eidasClientProperties = eidasClientProperties;
        this.singleSignOnService = singleSignOnService;
    }

    public void authenticate(HttpServletResponse response, String country, AssuranceLevel loa, String relayState) {
        validateCountry(country);
        validateRelayState(relayState);

        redirectUserForAuthentication(response, country, loa, relayState);
    }

    private void redirectUserForAuthentication(HttpServletResponse httpServletResponse, String country, AssuranceLevel loa, String relayState) {
        AuthnRequestBuilder authnRequestBuilder = new AuthnRequestBuilder(authnReqSigningCredential, eidasClientProperties, singleSignOnService);
        AuthnRequest authnRequest = authnRequestBuilder.buildAuthnRequest(loa);
        saveRequestAsSession(authnRequest);
        redirectUserWithRequest(httpServletResponse, authnRequest, country, relayState);
    }

    private void saveRequestAsSession(AuthnRequest authnRequest) {
        RequestSession requestSession = new RequestSession(authnRequest.getIssueInstant());
        requestSessionService.saveRequestSession(authnRequest.getID(), requestSession);
    }

    private void redirectUserWithRequest(HttpServletResponse httpServletResponse, AuthnRequest authnRequest, String country, String relayState) {
        MessageContext context = new MessageContext();

        context.setMessage(authnRequest);

        SAMLPeerEntityContext peerEntityContext = context.getSubcontext(SAMLPeerEntityContext.class, true);

        SAMLEndpointContext endpointContext = peerEntityContext.getSubcontext(SAMLEndpointContext.class, true);
        endpointContext.setEndpoint(singleSignOnService);

        SignatureSigningParameters signatureSigningParameters = new SignatureSigningParameters();
        signatureSigningParameters.setSigningCredential(authnReqSigningCredential);
        signatureSigningParameters.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);


        context.getSubcontext(SecurityParametersContext.class, true).setSignatureSigningParameters(signatureSigningParameters);

        EidasHTTPPostEncoder encoder = new EidasHTTPPostEncoder();
        encoder.setMessageContext(context);
        encoder.setCountryCode(country);
        encoder.setRelayState(relayState);

        encoder.setHttpServletResponse(httpServletResponse);

        try {
            encoder.initialize();
        } catch (ComponentInitializationException e) {
            throw new EidasClientException("Error initializing encoder", e);
        }

        LOGGER.info("AuthnRequest: ");
        LOGGER.info(OpenSAMLUtils.getXmlString(authnRequest));

        LOGGER.info("Redirecting to IDP");
        try {
            encoder.encode();
        } catch (MessageEncodingException e) {
            throw new EidasClientException("Error encoding HTTP POST Binding response", e);
        }
    }

    private void validateCountry(String country) {
        if (!eidasClientProperties.getAvailableCountries().contains(country)) {
            throw new InvalidEidasParamException("Invalid country! Valid countries:" + eidasClientProperties.getAvailableCountries());
        }
    }

    private void validateRelayState(String relayState) {
        if (relayState == null) {
            return;
        }
        Pattern pattern = Pattern.compile(RELAYSTATE_VALIDATION_REGEXP);
        Matcher matcher = pattern.matcher(relayState);
        if (!matcher.matches()) {
            throw new InvalidEidasParamException("Invalid RelayState! Must match the following regexp: " + RELAYSTATE_VALIDATION_REGEXP);
        }

    }

}
