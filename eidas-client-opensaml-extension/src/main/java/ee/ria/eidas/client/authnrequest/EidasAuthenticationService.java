package ee.ria.eidas.client.authnrequest;

import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.SignatureSigningParameters;
import org.opensaml.xmlsec.config.JavaCryptoValidationInitializer;
import org.opensaml.xmlsec.context.SecurityParametersContext;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Provider;
import java.security.Security;

public class EidasAuthenticationService {

    private static Logger LOGGER = LoggerFactory.getLogger(EidasAuthenticationService.class);

    private Credential authnReqSigningCredential;

    private EidasClientProperties eidasClientProperties;

    private SingleSignOnService singleSignOnService;

    static {
        init();
    }

    public EidasAuthenticationService(Credential authnReqSigningCredential, EidasClientProperties eidasClientProperties, SingleSignOnService singleSignOnService) {
        this.authnReqSigningCredential = authnReqSigningCredential;
        this.eidasClientProperties = eidasClientProperties;
        this.singleSignOnService = singleSignOnService;
    }

    public void authenticate(HttpServletRequest request, HttpServletResponse response, String country, AssuranceLevel loa, String relayState) {
        setGotoURLOnSession(request);
        redirectUserForAuthentication(response, country, loa, relayState);
    }

    private static void init() {
        JavaCryptoValidationInitializer javaCryptoValidationInitializer = new JavaCryptoValidationInitializer();
        try {
            javaCryptoValidationInitializer.init();
            for (Provider jceProvider : Security.getProviders()) {
                LOGGER.info(jceProvider.getInfo());
            }
            InitializationService.initialize();
        } catch (InitializationException e) {
            throw new EidasClientException("Error initializing Authentication service!", e);
        }
    }

    private void setGotoURLOnSession(HttpServletRequest request) {
        request.getSession().setAttribute(EidasClientProperties.SESSION_ATTRIBUTE_ORIGINALLY_REQUESTED_URL, request.getRequestURL().toString());
    }

    private void redirectUserForAuthentication(HttpServletResponse httpServletResponse, String country, AssuranceLevel loa, String relayState) {
        AuthnRequestBuilder authnRequestBuilder = new AuthnRequestBuilder(authnReqSigningCredential, eidasClientProperties, singleSignOnService);
        AuthnRequest authnRequest = authnRequestBuilder.buildAuthnRequest(loa) ;
        redirectUserWithRequest(httpServletResponse, authnRequest, country, relayState);
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

}
