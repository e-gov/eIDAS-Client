package ee.ria.eidas.client.authnrequest;

import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import org.joda.time.DateTime;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.impl.XSAnyBuilder;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.*;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;

public class AuthnRequestBuilder {

    private static Logger LOGGER = LoggerFactory.getLogger(AuthnRequestBuilder.class);

    private Credential authnReqSigningCredential;

    private EidasClientProperties eidasClientProperties;

    public AuthnRequestBuilder(Credential authnReqSigningCredential, EidasClientProperties eidasClientProperties) {
        this.authnReqSigningCredential = authnReqSigningCredential;
        this.eidasClientProperties = eidasClientProperties;
    }

    public AuthnRequest buildAuthnRequest() {
        try {
            Signature signature = (Signature) XMLObjectProviderRegistrySupport.getBuilderFactory()
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

            XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(authnRequest).marshall(authnRequest);
            Signer.signObject(signature);

            return authnRequest;
        } catch (Exception e) {
            throw new EidasClientException("Failed to create authnRequest: " + e.getMessage(), e);
        }
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

    private RequestedAuthnContext buildRequestedAuthnContext() {
        RequestedAuthnContext requestedAuthnContext = OpenSAMLUtils.buildSAMLObject(RequestedAuthnContext.class);
        requestedAuthnContext.setComparison(AuthnContextComparisonTypeEnumeration.MINIMUM);

        AuthnContextClassRef loaAuthnContextClassRef = OpenSAMLUtils.buildSAMLObject(AuthnContextClassRef.class);
        loaAuthnContextClassRef.setAuthnContextClassRef(AssuranceLevel.LOW.getUri());

        requestedAuthnContext.getAuthnContextClassRefs().add(loaAuthnContextClassRef);

        return requestedAuthnContext;
    }

    private Extensions buildExtensions() {
        Extensions extensions = OpenSAMLUtils.buildSAMLObject(Extensions.class);

        XSAny spType = new XSAnyBuilder().buildObject("http://eidas.europa.eu/saml-extensions", "SPType", "eidas");
        spType.setTextContent(eidasClientProperties.getSpType().getValue());
        extensions.getUnknownXMLObjects().add(spType);

        XSAny requestedAttributes = new XSAnyBuilder().buildObject("http://eidas.europa.eu/saml-extensions", "RequestedAttributes", "eidas");
        requestedAttributes.getUnknownXMLObjects().add(buildRequestedAttribute("DateOfBirth", "http://eidas.europa.eu/attributes/naturalperson/DateOfBirth", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri"));
        requestedAttributes.getUnknownXMLObjects().add(buildRequestedAttribute("LegalName", "http://eidas.europa.eu/attributes/legalperson/LegalName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri"));
        requestedAttributes.getUnknownXMLObjects().add(buildRequestedAttribute("LegalPersonIdentifier", "http://eidas.europa.eu/attributes/legalperson/LegalPersonIdentifier", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri"));
        requestedAttributes.getUnknownXMLObjects().add(buildRequestedAttribute("FamilyName", "http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri"));
        requestedAttributes.getUnknownXMLObjects().add(buildRequestedAttribute("FirstName", "http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri"));
        requestedAttributes.getUnknownXMLObjects().add(buildRequestedAttribute("PersonIdentifier", "http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier", "urn:oasis:names:tc:SAML:2.0:attrname-format:uri"));
        extensions.getUnknownXMLObjects().add(requestedAttributes);

        return extensions;
    }

    private XSAny buildRequestedAttribute(String friendlyName, String name, String nameFormat) {
        XSAny requestedAttribute = new XSAnyBuilder().buildObject("http://eidas.europa.eu/saml-extensions", "RequestedAttribute", "eidas");
        requestedAttribute.getUnknownAttributes().put(new QName("FriendlyName"), friendlyName);
        requestedAttribute.getUnknownAttributes().put(new QName("Name"), name);
        requestedAttribute.getUnknownAttributes().put(new QName("NameFormat"), nameFormat);
        requestedAttribute.getUnknownAttributes().put(new QName("isRequired"), "true");
        return requestedAttribute;
    }

}
