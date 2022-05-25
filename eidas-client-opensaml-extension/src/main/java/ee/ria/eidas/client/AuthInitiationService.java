package ee.ria.eidas.client;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.AuthnRequestBuilder;
import ee.ria.eidas.client.authnrequest.EidasAttribute;
import ee.ria.eidas.client.authnrequest.EidasHTTPPostEncoder;
import ee.ria.eidas.client.authnrequest.SPType;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.exception.InvalidRequestException;
import ee.ria.eidas.client.metadata.IDPMetadataResolver;
import ee.ria.eidas.client.session.RequestSession;
import ee.ria.eidas.client.session.RequestSessionService;
import ee.ria.eidas.client.session.UnencodedRequestSession;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.context.ApplicationEventPublisher;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ee.ria.eidas.client.authnrequest.EidasAttribute.*;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

@Slf4j
@RequiredArgsConstructor
public class AuthInitiationService {
    public static final List<EidasAttribute> DEFAULT_REQUESTED_ATTRIBUTE_SET = unmodifiableList(asList(CURRENT_FAMILY_NAME, CURRENT_GIVEN_NAME, DATE_OF_BIRTH, PERSON_IDENTIFIER));

    private static final Pattern RELAYSTATE_VALIDATION_REGEXP = Pattern.compile("^[a-zA-Z0-9-_]{0,80}$");
    private static final Pattern REQUESTER_ID_VALIDATION_REGEXP = Pattern.compile("^((?!urn:uuid:)[a-zA-Z][a-zA-Z0-9+.-]*:.*|urn:uuid:[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})$");

    private final RequestSessionService requestSessionService;

    private final Credential authnReqSigningCredential;

    private final EidasClientProperties eidasClientProperties;

    private final IDPMetadataResolver idpMetadataResolver;

    private final ApplicationEventPublisher applicationEventPublisher;

    public void authenticate(HttpServletResponse response,
                             String country,
                             AssuranceLevel loa,
                             String relayState,
                             String attributesSet,
                             SPType spType,
                             String requesterId) {
        validateCountry(country, spType);
        validateNullOrRegex("RelayState", relayState, RELAYSTATE_VALIDATION_REGEXP);
        validateRegex("RequesterID", requesterId, REQUESTER_ID_VALIDATION_REGEXP);
        List<EidasAttribute> eidasAttributes = determineEidasAttributes(attributesSet);
        redirectUserForAuthentication(response, country, loa, relayState, eidasAttributes, spType, requesterId);
    }

    private List<EidasAttribute> determineEidasAttributes(String attributesSet) {
        List<EidasAttribute> eidasAttributes = parseEidasAttributes(attributesSet);
        if (CollectionUtils.isEmpty(eidasAttributes)) {
            log.debug("No eIDAS attributes presented, using default (natural person) set: {}", DEFAULT_REQUESTED_ATTRIBUTE_SET);
            return new ArrayList<>(DEFAULT_REQUESTED_ATTRIBUTE_SET);
        }
        validateEidasAttributesAllowed(eidasAttributes);
        log.debug("Using following eIDAS attributes presented in the request: {}", eidasAttributes);
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

    private void redirectUserForAuthentication(
            HttpServletResponse httpServletResponse,
            String country,
            AssuranceLevel loa,
            String relayState,
            List<EidasAttribute> eidasAttributes,
            SPType spType,
            String requesterId) {
        AuthnRequestBuilder authnRequestBuilder = new AuthnRequestBuilder(authnReqSigningCredential, eidasClientProperties, idpMetadataResolver.getSingeSignOnService(), applicationEventPublisher);
        AuthnRequest authnRequest = authnRequestBuilder.buildAuthnRequest(loa, eidasAttributes, spType, requesterId);
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

        log.info("SAML request ID: {}", authnRequest.getID());
        if (log.isDebugEnabled()) {
            log.debug("AuthnRequest: {}", OpenSAMLUtils.getXmlString(authnRequest));
            log.debug("Redirecting to IDP");
        }

        try {
            encoder.encode();
        } catch (MessageEncodingException e) {
            throw new EidasClientException("Error encoding HTTP POST Binding response", e);
        }
    }

    private void validateCountry(String country, SPType spType) {
        Map<SPType, List<String>> validCountries = idpMetadataResolver.getSupportedCountries();
        List<String> validCountriesForCurrentSector = validCountries.get(spType);
        if (validCountriesForCurrentSector.stream().noneMatch(country::equalsIgnoreCase)) {
            throw new InvalidRequestException("Invalid country for " + spType + " sector! Valid countries:" + validCountriesForCurrentSector);
        }
    }

    private void validateNullOrRegex(String inputName, String inputValue, Pattern regex) {
        if (inputValue == null) {
            return;
        }
        validateRegex(inputName, inputValue, regex);
    }

    private void validateRegex(String inputName, String inputValue, Pattern regex) {
        if (inputValue == null) {
            throw new InvalidRequestException(inputName + " cannot be null!");
        }
        Matcher matcher = regex.matcher(inputValue);
        if (!matcher.matches()) {
            throw new InvalidRequestException("Invalid " + inputName + " (" + inputValue + ")! Must match the following regexp: " + regex);
        }
    }

}
