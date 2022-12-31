package ee.ria.eidas.client.authnrequest;

import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.config.EidasCredentialsConfiguration.FailedCredentialEvent;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import ee.ria.eidas.client.util.SAMLSigner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.context.ApplicationEventPublisher;

import javax.xml.namespace.QName;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class AuthnRequestBuilder {

    public static final String REQUESTED_ATTRIBUTE_NAME_FORMAT = "urn:oasis:names:tc:SAML:2.0:attrname-format:uri";

    private final Credential authnReqSigningCredential;

    private final EidasClientProperties eidasClientProperties;

    private final SingleSignOnService singleSignOnService;

    private final ApplicationEventPublisher applicationEventPublisher;

    public AuthnRequest buildAuthnRequest(AssuranceLevel loa, List<EidasAttribute> eidasAttributes, SPType spType, String requesterId, String country) {
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
            authnRequest.setRequestedAuthnContext(buildRequestedAuthnContext(loa, country));
            authnRequest.setExtensions(buildExtensions(eidasAttributes, spType, requesterId));

            addSignature(authnRequest);

            log.info("AuthnRequest building succeeded. Request ID: {}", authnRequest.getID());
            if (log.isDebugEnabled()) {
                log.debug("AuthnRequest: {}", OpenSAMLUtils.getXmlString(authnRequest));
            }

            return authnRequest;
        } catch (Exception e) {
            applicationEventPublisher.publishEvent(new FailedCredentialEvent(authnReqSigningCredential));
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

    private RequestedAuthnContext buildRequestedAuthnContext(AssuranceLevel loa, String country) {
        if (loa == null) {
            loa = eidasClientProperties.getDefaultLoa();
        }

        AuthnContextClassRef loaAuthnContextClassRef = OpenSAMLUtils.buildSAMLObject(AuthnContextClassRef.class);
        loaAuthnContextClassRef.setAuthnContextClassRef(loa.getUri());
        RequestedAuthnContext requestedAuthnContext = OpenSAMLUtils.buildSAMLObject(RequestedAuthnContext.class);
        requestedAuthnContext.setComparison(AuthnContextComparisonTypeEnumeration.MINIMUM);
        requestedAuthnContext.getAuthnContextClassRefs().add(loaAuthnContextClassRef);

        Optional<NonNotifiedAssuranceLevel> nonNotifiedLevel = getNonNotifiedLevel(loa, country);
        if (nonNotifiedLevel.isPresent()) {
            loaAuthnContextClassRef.setAuthnContextClassRef(nonNotifiedLevel.get().getNonNotifiedLevel());
            requestedAuthnContext.setComparison(AuthnContextComparisonTypeEnumeration.EXACT);
        }
        return requestedAuthnContext;
    }

    private Optional<NonNotifiedAssuranceLevel> getNonNotifiedLevel(AssuranceLevel loa, String country) {
        return eidasClientProperties.getNonNotifiedAssuranceLevels().stream()
                .filter(c -> c.getCountry().equals(country))
                .filter(c -> loa.getLevel() <= AssuranceLevel.toEnum(c.getNotifiedLevel()).getLevel())
                .findFirst();
    }

    private Extensions buildExtensions(List<EidasAttribute> eidasAttributes, SPType spTypeValue, String requesterIdValue) {
        Extensions extensions = OpenSAMLUtils.buildSAMLObject(Extensions.class);

        XSAny spType = new XSAnyBuilder().buildObject("http://eidas.europa.eu/saml-extensions", "SPType", "eidas");
        spType.setTextContent(spTypeValue.getValue());
        extensions.getUnknownXMLObjects().add(spType);

        XSAny requesterId = new XSAnyBuilder().buildObject("http://eidas.europa.eu/saml-extensions", "RequesterID", "eidas");
        requesterId.setTextContent(requesterIdValue);
        extensions.getUnknownXMLObjects().add(requesterId);

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
