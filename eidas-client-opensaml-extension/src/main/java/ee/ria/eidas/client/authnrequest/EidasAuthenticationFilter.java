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
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.*;
import org.opensaml.saml.saml2.metadata.Endpoint;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.SignatureSigningParameters;
import org.opensaml.xmlsec.config.JavaCryptoValidationInitializer;
import org.opensaml.xmlsec.context.SecurityParametersContext;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Provider;
import java.security.Security;

public class EidasAuthenticationFilter implements Filter {

    private static Logger LOGGER = LoggerFactory.getLogger(EidasAuthenticationFilter.class);

    private Credential authnReqSigningCredential;

    private EidasClientProperties eidasClientProperties;

    public EidasAuthenticationFilter(Credential authnReqSigningCredential, EidasClientProperties eidasClientProperties) {
        this.authnReqSigningCredential = authnReqSigningCredential;
        this.eidasClientProperties = eidasClientProperties;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        JavaCryptoValidationInitializer javaCryptoValidationInitializer = new JavaCryptoValidationInitializer();
        try {
            javaCryptoValidationInitializer.init();
        } catch (InitializationException e) {
            throw new EidasClientException("Error initializing IDP Metadata Provider", e);
        }

        for (Provider jceProvider : Security.getProviders()) {
            LOGGER.info(jceProvider.getInfo());
        }

        try {
            LOGGER.info("Initializing");
            InitializationService.initialize();
        } catch (InitializationException e) {
            throw new RuntimeException("Initialization failed");
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest)request;
        HttpServletResponse httpServletResponse = (HttpServletResponse)response;

        if (httpServletRequest.getSession().getAttribute(EidasClientProperties.SESSION_ATTRIBUTE_USER_AUTHENTICATED) != null) {
            chain.doFilter(request, response);
        } else {
            setGotoURLOnSession(httpServletRequest);
            redirectUserForAuthentication(httpServletResponse);
        }
    }

    @Override
    public void destroy() {

    }

    private void setGotoURLOnSession(HttpServletRequest request) {
        request.getSession().setAttribute(EidasClientProperties.SESSION_ATTRIBUTE_ORIGINALLY_REQUESTED_URL, request.getRequestURL().toString());
    }

    private void redirectUserForAuthentication(HttpServletResponse httpServletResponse) {
        AuthnRequestBuilder authnRequestBuilder = new AuthnRequestBuilder(authnReqSigningCredential, eidasClientProperties);
        AuthnRequest authnRequest = authnRequestBuilder.buildAuthnRequest() ;
        redirectUserWithRequest(httpServletResponse, authnRequest);
    }

    private void redirectUserWithRequest(HttpServletResponse httpServletResponse, AuthnRequest authnRequest) {
        MessageContext context = new MessageContext();

        context.setMessage(authnRequest);

        SAMLPeerEntityContext peerEntityContext = context.getSubcontext(SAMLPeerEntityContext.class, true);

        SAMLEndpointContext endpointContext = peerEntityContext.getSubcontext(SAMLEndpointContext.class, true);
        endpointContext.setEndpoint(getIPDEndpoint());

        SignatureSigningParameters signatureSigningParameters = new SignatureSigningParameters();
        signatureSigningParameters.setSigningCredential(authnReqSigningCredential);
        signatureSigningParameters.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);


        context.getSubcontext(SecurityParametersContext.class, true).setSignatureSigningParameters(signatureSigningParameters);

        EidasHTTPPostEncoder encoder = new EidasHTTPPostEncoder();
        encoder.setMessageContext(context);
        encoder.setCountryCode("CA");
        encoder.setRelayState("123");

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

    private Endpoint getIPDEndpoint() {
        SingleSignOnService endpoint = OpenSAMLUtils.buildSAMLObject(SingleSignOnService.class);
        endpoint.setBinding(SAMLConstants.SAML2_POST_BINDING_URI);
        endpoint.setLocation(eidasClientProperties.getIdpSSOUrl());
        return endpoint;
    }
}