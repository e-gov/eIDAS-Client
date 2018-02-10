package ee.ria.eidas;

import ee.ria.eidas.config.EidasClientProperties;
import ee.ria.eidas.config.OpenSamlConfiguration;
import ee.ria.eidas.util.CustomHTTPPostEncoder;
import ee.ria.eidas.util.OpenSAMLUtils;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.joda.time.DateTime;
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
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import se.litsec.eidas.opensaml.ext.RequestedAttribute;
import se.litsec.eidas.opensaml.ext.RequestedAttributes;
import se.litsec.eidas.opensaml.ext.SPType;
import se.litsec.eidas.opensaml.ext.SPTypeEnumeration;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Provider;
import java.security.Security;

public class AccessFilter implements Filter {
    private static Logger logger = LoggerFactory.getLogger(AccessFilter.class);

    private Credential authnReqSigningCredential;

    private EidasClientProperties eidasClientProperties;

    public AccessFilter (Credential authnReqSigningCredential, EidasClientProperties eidasClientProperties) {
        this.authnReqSigningCredential = authnReqSigningCredential;
        this.eidasClientProperties = eidasClientProperties;
    }

    public void init(FilterConfig filterConfig) throws ServletException {
        JavaCryptoValidationInitializer javaCryptoValidationInitializer = new JavaCryptoValidationInitializer();
        try {
            javaCryptoValidationInitializer.init();
        } catch (InitializationException e) {
            e.printStackTrace();
        }

        for (Provider jceProvider : Security.getProviders()) {
            logger.info(jceProvider.getInfo());
        }

        try {
            logger.info("Initializing");
            InitializationService.initialize();
        } catch (InitializationException e) {
            throw new RuntimeException("Initialization failed");
        }
    }

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

    private void setGotoURLOnSession(HttpServletRequest request) {
        request.getSession().setAttribute(EidasClientProperties.SESSION_ATTRIBUTE_ORIGINALLY_REQUESTED_URL, request.getRequestURL().toString());
    }

    private void redirectUserForAuthentication(HttpServletResponse httpServletResponse) {
        AuthnRequest authnRequest = buildAuthnRequest();
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

        CustomHTTPPostEncoder encoder = new CustomHTTPPostEncoder();
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocityEngine.init();

        encoder.setVelocityEngine(velocityEngine);
        encoder.setMessageContext(context);
        encoder.setCountryCode("CA");
        encoder.setRelayState("123");

        encoder.setHttpServletResponse(httpServletResponse);

        try {
            encoder.initialize();
        } catch (ComponentInitializationException e) {
            throw new RuntimeException(e);
        }

        logger.info("AuthnRequest: ");
        OpenSAMLUtils.logSAMLObject(authnRequest);

        logger.info("Redirecting to IDP");
        try {
            encoder.encode();
        } catch (MessageEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private AuthnRequest buildAuthnRequest() {

        try {

            Signature signature = (Signature) OpenSamlConfiguration.getBuilderFactory()
                    .getBuilder(Signature.DEFAULT_ELEMENT_NAME)
                    .buildObject(Signature.DEFAULT_ELEMENT_NAME);
            signature.setSigningCredential(authnReqSigningCredential);
            signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA512);
            signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

            X509KeyInfoGeneratorFactory x509KeyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
            x509KeyInfoGeneratorFactory.setEmitEntityCertificate(true);
            KeyInfo keyInfo = x509KeyInfoGeneratorFactory.newInstance().generate(authnReqSigningCredential);
            signature.setKeyInfo(keyInfo);


            AuthnRequest authnRequest = OpenSAMLUtils.buildSAMLObject(AuthnRequest.class);
            authnRequest.setIssueInstant(new DateTime());
            authnRequest.setForceAuthn(true);
            authnRequest.setProviderName(eidasClientProperties.getProviderName());
            authnRequest.setDestination(eidasClientProperties.getIdpSSOUrl());
            authnRequest.setProtocolBinding(SAMLConstants.SAML2_POST_BINDING_URI);
            authnRequest.setAssertionConsumerServiceURL(eidasClientProperties.getCallbackUrl());
            authnRequest.setID(OpenSAMLUtils.generateSecureRandomId());
            authnRequest.setIssuer(buildIssuer());
            authnRequest.setNameIDPolicy(buildNameIdPolicy());
            authnRequest.setRequestedAuthnContext(buildRequestedAuthnContext());
            authnRequest.setExtensions(buildExtensions());
            authnRequest.setSignature(signature);


            final Element entityDescriptorElement = OpenSamlConfiguration.getMarshallerFactory().getMarshaller(authnRequest).marshall(authnRequest);
            Signer.signObject(signature);
            return authnRequest;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create authnRequest: " + e.getMessage(), e);
        }


    }

    private Extensions buildExtensions() {
        Extensions extensions = OpenSAMLUtils.buildSAMLObject(Extensions.class);
        SPType spTypeObj = (SPType) OpenSAMLUtils.buildSAMLObject(SPType.class);
        spTypeObj.setType(SPTypeEnumeration.PUBLIC);
        extensions.getUnknownXMLObjects().add(spTypeObj);

        RequestedAttributes requestedAttributes = (RequestedAttributes) OpenSAMLUtils.buildSAMLObject(RequestedAttributes.class);
        requestedAttributes.getRequestedAttributes().add(buildRequestedAttribute("DateOfBirth", "http://eidas.europa.eu/attributes/naturalperson/DateOfBirth", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri"));
        requestedAttributes.getRequestedAttributes().add(buildRequestedAttribute("LegalName", "http://eidas.europa.eu/attributes/legalperson/LegalName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri"));
        requestedAttributes.getRequestedAttributes().add(buildRequestedAttribute("LegalPersonIdentifier", "http://eidas.europa.eu/attributes/legalperson/LegalPersonIdentifier", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri"));
        requestedAttributes.getRequestedAttributes().add(buildRequestedAttribute("FamilyName", "http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri"));
        requestedAttributes.getRequestedAttributes().add(buildRequestedAttribute("FirstName", "http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri"));
        requestedAttributes.getRequestedAttributes().add(buildRequestedAttribute("PersonIdentifier", "http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri"));
        extensions.getUnknownXMLObjects().add(requestedAttributes);

        return extensions;
    }

    private RequestedAttribute buildRequestedAttribute(String friendlyName, String name, String nameFormat) {
        RequestedAttribute requestedAttribute = (RequestedAttribute) OpenSAMLUtils.buildSAMLObject(RequestedAttribute.class);
        requestedAttribute.setIsRequired(true);
        requestedAttribute.setFriendlyName(friendlyName);
        requestedAttribute.setName(name);
        requestedAttribute.setNameFormat(nameFormat);
        return requestedAttribute;
    }

    private RequestedAuthnContext buildRequestedAuthnContext() {
        RequestedAuthnContext requestedAuthnContext = OpenSAMLUtils.buildSAMLObject(RequestedAuthnContext.class);
        requestedAuthnContext.setComparison(AuthnContextComparisonTypeEnumeration.MINIMUM);

        AuthnContextClassRef loaAuthnContextClassRef = OpenSAMLUtils.buildSAMLObject(AuthnContextClassRef.class);
        loaAuthnContextClassRef.setAuthnContextClassRef("http://eidas.europa.eu/LoA/low");

        requestedAuthnContext.getAuthnContextClassRefs().add(loaAuthnContextClassRef);

        return requestedAuthnContext;

    }

    private NameIDPolicy buildNameIdPolicy() {
        NameIDPolicy nameIDPolicy = OpenSAMLUtils.buildSAMLObject(NameIDPolicy.class);
        nameIDPolicy.setAllowCreate(true);
        nameIDPolicy.setFormat(NameIDType.UNSPECIFIED);
        return nameIDPolicy;
    }

    private Issuer buildIssuer() {
        Issuer issuer = OpenSAMLUtils.buildSAMLObject(Issuer.class);
        issuer.setValue(eidasClientProperties.getSpEntityId());
        return issuer;
    }

    private Endpoint getIPDEndpoint() {
        SingleSignOnService endpoint = OpenSAMLUtils.buildSAMLObject(SingleSignOnService.class);
        endpoint.setBinding(SAMLConstants.SAML2_POST_BINDING_URI);
        endpoint.setLocation(eidasClientProperties.getIdpSSOUrl());
        return endpoint;
    }

    public void destroy() {

    }
}