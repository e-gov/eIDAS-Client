package ee.ria.eidas.client.authnrequest;

import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import ee.ria.eidas.client.util.SAMLSigner;
import org.joda.time.DateTime;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.impl.XSAnyBuilder;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnContextComparisonTypeEnumeration;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Extensions;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameIDPolicy;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.SecurityException;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.util.List;

public class AuthnRequestBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthnRequestBuilder.class);

    public static final String REQUESTED_ATTRIBUTE_NAME_FORMAT = "urn:oasis:names:tc:SAML:2.0:attrname-format:uri";

    private Credential authnReqSigningCredential;

    private EidasClientProperties eidasClientProperties;

    private SingleSignOnService singleSignOnService;

    public AuthnRequestBuilder(Credential authnReqSigningCredential, EidasClientProperties eidasClientProperties, SingleSignOnService singleSignOnService) {
        this.authnReqSigningCredential = authnReqSigningCredential;
        this.eidasClientProperties = eidasClientProperties;
        this.singleSignOnService = singleSignOnService;
    }

    public AuthnRequest buildAuthnRequest(AssuranceLevel loa, List<EidasAttribute> eidasAttributes) {
        try {
            AuthnRequest authnRequest = OpenSAMLUtils.buildSAMLObject(AuthnRequest.class);
            authnRequest.setIssueInstant(new DateTime());
            authnRequest.setForceAuthn(true);
            authnRequest.setIsPassive(false);
            authnRequest.setProviderName(eidasClientProperties.getProviderName());
            authnRequest.setDestination(singleSignOnService.getLocation());
            authnRequest.setProtocolBinding(SAMLConstants.SAML2_POST_BINDING_URI);
            authnRequest.setAssertionConsumerServiceURL(eidasClientProperties.getCallbackUrl());
            authnRequest.setID(OpenSAMLUtils.generateSecureRandomId());
            authnRequest.setIssuer(buildIssuer());
            authnRequest.setNameIDPolicy(buildNameIdPolicy());
            authnRequest.setRequestedAuthnContext(buildRequestedAuthnContext(loa));
            authnRequest.setExtensions(buildExtensions(eidasAttributes));

            addSignature(authnRequest);

            LOGGER.info("AuthnRequest building succeeded. Request ID: {}", authnRequest.getID());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("AuthnRequest: {}", OpenSAMLUtils.getXmlString(authnRequest));
            }

            return authnRequest;
        } catch (Exception e) {
            throw new EidasClientException("Failed to create authnRequest: " + e.getMessage(), e);
        }
    }

    private void addSignature(AuthnRequest authnRequest) throws SecurityException, MarshallingException, SignatureException {
        new SAMLSigner(eidasClientProperties.getRequestSignatureAlgorithm(), authnReqSigningCredential).sign(authnRequest);
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

    private RequestedAuthnContext buildRequestedAuthnContext(AssuranceLevel loa) {
        RequestedAuthnContext requestedAuthnContext = OpenSAMLUtils.buildSAMLObject(RequestedAuthnContext.class);
        requestedAuthnContext.setComparison(AuthnContextComparisonTypeEnumeration.MINIMUM);

        AuthnContextClassRef loaAuthnContextClassRef = OpenSAMLUtils.buildSAMLObject(AuthnContextClassRef.class);

        if (loa == null) {
            loa = eidasClientProperties.getDefaultLoa();
        }
        loaAuthnContextClassRef.setAuthnContextClassRef(loa.getUri());

        requestedAuthnContext.getAuthnContextClassRefs().add(loaAuthnContextClassRef);

        return requestedAuthnContext;
    }

    private Extensions buildExtensions(List<EidasAttribute> eidasAttributes) {
        Extensions extensions = OpenSAMLUtils.buildSAMLObject(Extensions.class);

        XSAny spType = new XSAnyBuilder().buildObject("http://eidas.europa.eu/saml-extensions", "SPType", "eidas");
        spType.setTextContent(eidasClientProperties.getSpType().getValue());
        extensions.getUnknownXMLObjects().add(spType);

        XSAny requestedAttributes = new XSAnyBuilder().buildObject("http://eidas.europa.eu/saml-extensions", "RequestedAttributes", "eidas");
        addEidasAttributes(eidasAttributes, requestedAttributes);
        extensions.getUnknownXMLObjects().add(requestedAttributes);

        return extensions;
    }

    private void addEidasAttributes(List<EidasAttribute> eidasAttributes, XSAny requestedAttributes) {
        if (eidasAttributes == null)
            return;

        for (EidasAttribute attribute : eidasAttributes) {
            addEidasAttribute(requestedAttributes, attribute);
        }
    }

    private void addEidasAttribute(XSAny requestedAttributes, EidasAttribute attribute) {
        requestedAttributes.getUnknownXMLObjects().add(buildRequestedAttribute(attribute));
    }

    private XSAny buildRequestedAttribute(EidasAttribute eidasAttribute) {
        XSAny requestedAttribute = new XSAnyBuilder().buildObject("http://eidas.europa.eu/saml-extensions", "RequestedAttribute", "eidas");
        requestedAttribute.getUnknownAttributes().put(new QName("FriendlyName"), eidasAttribute.getFriendlyName());
        requestedAttribute.getUnknownAttributes().put(new QName("Name"), eidasAttribute.getName());
        requestedAttribute.getUnknownAttributes().put(new QName("NameFormat"), REQUESTED_ATTRIBUTE_NAME_FORMAT);
        requestedAttribute.getUnknownAttributes().put(new QName("isRequired"), eidasAttribute.isRequired() ? "true" : "false");
        return requestedAttribute;
    }

}
