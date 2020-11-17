package ee.ria.eidas.client;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.AuthnRequestBuilder;
import ee.ria.eidas.client.authnrequest.EidasAttribute;
import ee.ria.eidas.client.authnrequest.EidasHTTPPostEncoder;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.exception.InvalidRequestException;
import ee.ria.eidas.client.metadata.IDPMetadataResolver;
import ee.ria.eidas.client.session.RequestSession;
import ee.ria.eidas.client.session.RequestSessionService;
import ee.ria.eidas.client.session.UnencodedRequestSession;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import org.apache.commons.collections.CollectionUtils;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.SignatureSigningParameters;
import org.opensaml.xmlsec.context.SecurityParametersContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AuthInitiationService {

    public static final List<EidasAttribute> DEFAULT_REQUESTED_ATTRIBUTE_SET = Collections.unmodifiableList(Arrays.asList(EidasAttribute.CURRENT_FAMILY_NAME, EidasAttribute.CURRENT_GIVEN_NAME, EidasAttribute.DATE_OF_BIRTH, EidasAttribute.PERSON_IDENTIFIER));

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthInitiationService.class);

    private static final String RELAYSTATE_VALIDATION_REGEXP = "^[a-zA-Z0-9-_]{0,80}$";

    private RequestSessionService requestSessionService;

    private Credential authnReqSigningCredential;

    private EidasClientProperties eidasClientProperties;

    private IDPMetadataResolver idpMetadataResolver;



    public AuthInitiationService(RequestSessionService requestSessionService, Credential authnReqSigningCredential, EidasClientProperties eidasClientProperties, IDPMetadataResolver idpMetadataResolver) {
        this.requestSessionService = requestSessionService;
        this.authnReqSigningCredential = authnReqSigningCredential;
        this.eidasClientProperties = eidasClientProperties;
        this.idpMetadataResolver = idpMetadataResolver;
    }

    public void authenticate(HttpServletResponse response, String country, AssuranceLevel loa, String relayState, String attributesSet) {
        validateCountry(country);
        validateRelayState(relayState);
        List<EidasAttribute> eidasAttributes = determineEidasAttributes(attributesSet);
        redirectUserForAuthentication(response, country, loa, relayState, eidasAttributes);
    }

    private List<EidasAttribute> determineEidasAttributes(String attributesSet) {
        List<EidasAttribute> eidasAttributes = parseEidasAttributes(attributesSet);
        if (CollectionUtils.isEmpty(eidasAttributes)) {
            LOGGER.debug("No eIDAS attributes presented, using default (natural person) set: {}", DEFAULT_REQUESTED_ATTRIBUTE_SET);
            return new ArrayList<>(DEFAULT_REQUESTED_ATTRIBUTE_SET);
        }
        validateEidasAttributesAllowed(eidasAttributes);
        LOGGER.debug("Using following eIDAS attributes presented in the request: {}", eidasAttributes);
        return eidasAttributes;
    }

    private List<EidasAttribute> parseEidasAttributes(String attributesSet) {
        if (attributesSet == null) {
            return new ArrayList<>();
        }

        try {
            return Arrays.stream(attributesSet.split(" ")).map(EidasAttribute::fromString).collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            List<String> validEidasAttributes = Arrays.stream(EidasAttribute.values()).map(EidasAttribute::getFriendlyName).collect(Collectors.toList());
            throw new InvalidRequestException("Found one or more invalid Attributes value(s). Valid values are: " + validEidasAttributes, e);
        }
    }

    private void validateEidasAttributesAllowed(List<EidasAttribute> eidasAttributes) {
        List<EidasAttribute> allowedEidasAttributes = eidasClientProperties.getAllowedEidasAttributes();
        for (EidasAttribute eidasAttribute : eidasAttributes) {
            if (!allowedEidasAttributes.contains(eidasAttribute)) {
                throw new InvalidRequestException("Attributes value '" + eidasAttribute.getFriendlyName() + "' is not allowed. Allowed values are: " +
                        allowedEidasAttributes.stream().map(EidasAttribute::getFriendlyName).collect(Collectors.toList()));
            }
        }
    }

    private void redirectUserForAuthentication(HttpServletResponse httpServletResponse, String country, AssuranceLevel loa, String relayState, List<EidasAttribute> eidasAttributes) {
        AuthnRequestBuilder authnRequestBuilder = new AuthnRequestBuilder(authnReqSigningCredential, eidasClientProperties, idpMetadataResolver.getSingeSignOnService());
        AuthnRequest authnRequest = authnRequestBuilder.buildAuthnRequest(loa, eidasAttributes);
        saveRequestAsSession(authnRequest, eidasAttributes);
        redirectUserWithRequest(httpServletResponse, authnRequest, country, relayState);
    }

    private void saveRequestAsSession(AuthnRequest authnRequest, List<EidasAttribute> eidasAttributes) {
        String loa = authnRequest.getRequestedAuthnContext().getAuthnContextClassRefs().get(0).getAuthnContextClassRef();
        RequestSession requestSession = new UnencodedRequestSession(authnRequest.getID(), authnRequest.getIssueInstant(), AssuranceLevel.toEnum(loa), eidasAttributes);
        requestSessionService.saveRequestSession(requestSession.getRequestId(), requestSession);
    }

    private void redirectUserWithRequest(HttpServletResponse httpServletResponse, AuthnRequest authnRequest, String country, String relayState) {
        MessageContext context = new MessageContext();

        context.setMessage(authnRequest);

        SAMLPeerEntityContext peerEntityContext = context.getSubcontext(SAMLPeerEntityContext.class, true);

        SAMLEndpointContext endpointContext = peerEntityContext.getSubcontext(SAMLEndpointContext.class, true);
        endpointContext.setEndpoint(idpMetadataResolver.getSingeSignOnService());

        SignatureSigningParameters signatureSigningParameters = new SignatureSigningParameters();
        signatureSigningParameters.setSigningCredential(authnReqSigningCredential);
        signatureSigningParameters.setSignatureAlgorithm(eidasClientProperties.getRequestSignatureAlgorithm());


        context.getSubcontext(SecurityParametersContext.class, true).setSignatureSigningParameters(signatureSigningParameters);

        EidasHTTPPostEncoder encoder = new EidasHTTPPostEncoder();
        encoder.setMessageContext(context);
        encoder.setCountryCode(country.toUpperCase());
        encoder.setRelayState(relayState);

        encoder.setHttpServletResponse(httpServletResponse);

        try {
            encoder.initialize();
        } catch (ComponentInitializationException e) {
            throw new EidasClientException("Error initializing encoder", e);
        }

        LOGGER.info("SAML request ID: " + authnRequest.getID());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("AuthnRequest: {}", OpenSAMLUtils.getXmlString(authnRequest));
            LOGGER.debug("Redirecting to IDP");
        }

        try {
            encoder.encode();
        } catch (MessageEncodingException e) {
            throw new EidasClientException("Error encoding HTTP POST Binding response", e);
        }
    }

    private void validateCountry(String country) {
        List<String> validCountries = idpMetadataResolver.getSupportedCountries();
        if (!validCountries.stream().anyMatch(country::equalsIgnoreCase)) {
            throw new InvalidRequestException("Invalid country! Valid countries:" + validCountries);
        }
    }

    private void validateRelayState(String relayState) {
        if (relayState == null) {
            return;
        }
        Pattern pattern = Pattern.compile(RELAYSTATE_VALIDATION_REGEXP);
        Matcher matcher = pattern.matcher(relayState);
        if (!matcher.matches()) {
            throw new InvalidRequestException("Invalid RelayState! Must match the following regexp: " + RELAYSTATE_VALIDATION_REGEXP);
        }

    }

}
