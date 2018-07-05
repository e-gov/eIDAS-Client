package ee.ria.eidas.client.response;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.EidasAttribute;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.InvalidRequestException;
import ee.ria.eidas.client.session.RequestSession;
import org.apache.commons.collections.CollectionUtils;
import org.joda.time.DateTime;
import org.opensaml.saml.saml2.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class AssertionValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssertionValidator.class);

    private int acceptedClockSkew;
    private String idpMetadataUrl;
    private String callbackUrl;
    private String spEntityID;
    private int maxAuthenticationLifetime;

    public AssertionValidator(EidasClientProperties properties) {
        this.acceptedClockSkew = properties.getAcceptedClockSkew();
        this.maxAuthenticationLifetime = properties.getMaximumAuthenticationLifetime();
        this.idpMetadataUrl = properties.getIdpMetadataUrl();
        this.callbackUrl = properties.getCallbackUrl();
        this.spEntityID = properties.getSpEntityId();
    }

    public void validate(Assertion assertion, RequestSession requestSession) {
        validateEidasRestrictions(assertion);
        validateIssueInstant(assertion);
        validateIssuer(assertion.getIssuer());
        validateSubject(assertion.getSubject());
        validateExistingRequestSession(assertion, requestSession);
        validateConditions(assertion.getConditions());
        validateAuthnStatements(assertion.getAuthnStatements());
    }

    private void validateRequestedMandatoryEidasDatasetsPresent(List<EidasAttribute> requestedAttributes, Assertion assertion) {
        Set<EidasAttribute> attributesInAssertion = getAttributesPresentInAssertion(assertion);
        Collection<EidasAttribute> missingAttributes = CollectionUtils.subtract(requestedAttributes, attributesInAssertion);
        if (!missingAttributes.isEmpty()) {
            throw new InvalidRequestException("Missing mandatory attributes in the response assertion: " + missingAttributes.stream().map(EidasAttribute::getFriendlyName).collect(Collectors.toList()));
        }
    }

    private Set<EidasAttribute> getAttributesPresentInAssertion(Assertion assertion) {
        Set<EidasAttribute> attributes = new LinkedHashSet<>();
        for (Attribute attribute : assertion.getAttributeStatements().get(0).getAttributes()) {
            try {
                attributes.add(EidasAttribute.fromString(attribute.getFriendlyName()));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Assertion contains unrecognized attribute with FriendlyName: " + attribute.getFriendlyName());
            }
        }
        return attributes;
    }

    private void validateExistingRequestSession(Assertion assertion, RequestSession requestSession) {
        String requestID = assertion.getSubject().getSubjectConfirmations().get(0).getSubjectConfirmationData().getInResponseTo();
        if (requestSession == null || !requestSession.getRequestId().equals(requestID)) {
            throw new InvalidRequestException("No corresponding SAML request session found for the given response assertion!");
        } else {
            DateTime now = new DateTime(requestSession.getIssueInstant().getZone());
            if (now.isAfter(requestSession.getIssueInstant().plusSeconds(maxAuthenticationLifetime).plusSeconds(acceptedClockSkew))) {
                throw new InvalidRequestException("Request session with ID " + requestID + " has expired!");
            }
        }

        boolean isReturnedLoaValid = false;
        for (AssuranceLevel loa : AssuranceLevel.values()) {
            if (loa.getUri().equalsIgnoreCase(assertion.getAuthnStatements().get(0).getAuthnContext().getAuthnContextClassRef().getAuthnContextClassRef())
                    &&  loa.getLevel() >= requestSession.getLoa().getLevel()) {
                isReturnedLoaValid = true;
            }
        }
        if (!isReturnedLoaValid) {
            throw new InvalidRequestException("AuthnContextClassRef is not greater or equal to the request level of assurance!");
        }

        validateRequestedMandatoryEidasDatasetsPresent(requestSession.getRequestedAttributes().stream().filter(EidasAttribute::isRequired).collect(Collectors.toList()), assertion);
    }

    private void validateEidasRestrictions(Assertion assertion) {
        if (assertion.getAuthnStatements() == null || assertion.getAuthnStatements().size() != 1 ) {
            throw new InvalidRequestException("Assertion must contain exactly 1 AuthnStatement!");
        }
        if (assertion.getAttributeStatements() == null || assertion.getAttributeStatements().size() != 1 ) {
            throw new InvalidRequestException("Assertion must contain exactly 1 AttributeStatement!");
        }
        if (assertion.getAuthnStatements().get(0).getAuthnContext().getAuthnContextClassRef() == null) {
            throw new InvalidRequestException("Authncontext must contain AuthnContextClassRef!");
        }
    }

    private void validateIssueInstant(Assertion assertion) {
        DateTime now = new DateTime(assertion.getIssueInstant().getZone());
        if (now.isAfter(assertion.getIssueInstant().plusSeconds(acceptedClockSkew).plusSeconds(maxAuthenticationLifetime))) {
            throw new InvalidRequestException("Assertion issue instant is expired!");
        } else if (now.isBefore(assertion.getIssueInstant().minusSeconds(acceptedClockSkew).minusSeconds(maxAuthenticationLifetime))) {
            throw new InvalidRequestException("Assertion issue instant is in the future!");
        }
    }

    private void validateIssuer(Issuer issuer) {
        if (issuer == null) {
            throw new InvalidRequestException("Assertion is missing issuer!");
        } else if (issuer.getValue() == null || !issuer.getValue().equals(idpMetadataUrl)) {
            throw new InvalidRequestException("Assertion issuer's value is not equal to the configured IDP metadata url!");
        } else if (issuer.getFormat() == null || !NameIDType.ENTITY.equals(issuer.getFormat())) {
            throw new InvalidRequestException("Assertion issuer's format must equal to: " + NameIDType.ENTITY + "!");
        }
    }

    private void validateSubject(Subject subject) {
        if (subject == null) {
            throw new InvalidRequestException("Assertion is missing subject!");
        }
        validateSubjectNameId(subject.getNameID());
        validateSubjectConfirmation(subject.getSubjectConfirmations());
    }

    private void validateSubjectNameId(NameID nameID) {
        if (nameID == null) {
            throw new InvalidRequestException("Assertion subject is missing nameID!");
        }
        List<String> validNameIDFormats = Arrays.asList(NameIDType.UNSPECIFIED , NameIDType.TRANSIENT, NameIDType.PERSISTENT);
        if (!validNameIDFormats.contains(nameID.getFormat())) {
            throw new InvalidRequestException("Assertion's subject name ID format is not equal to one of the following: " + validNameIDFormats);
        }
    }

    private void validateSubjectConfirmation(List<SubjectConfirmation> subjectConfirmations) {
        if (subjectConfirmations == null || subjectConfirmations.size() != 1) {
            throw new InvalidRequestException("Assertion subject must contain exactly 1 SubjectConfirmation!");
        }
        if (!SubjectConfirmation.METHOD_BEARER.equals(subjectConfirmations.get(0).getMethod())) {
            throw new InvalidRequestException("Assertion SubjectConfirmation must equal to: " + SubjectConfirmation.METHOD_BEARER + "!");
        }

        SubjectConfirmationData subjectConfirmationData = subjectConfirmations.get(0).getSubjectConfirmationData();
        if (subjectConfirmationData == null) {
            throw new InvalidRequestException("Assertion's subject SubjectConfirmation!");
        }
        validateNotOnOrAfter(subjectConfirmationData);
        validateRecipient(subjectConfirmationData);
    }

    private void validateNotOnOrAfter(SubjectConfirmationData subjectConfirmationData) {
        DateTime now = new DateTime(subjectConfirmationData.getNotOnOrAfter().getZone());
        if (subjectConfirmationData.getNotOnOrAfter().plusSeconds(acceptedClockSkew).isBefore(now)) {
            throw new InvalidRequestException("SubjectConfirmationData NotOnOrAfter is not valid!");
        }
    }

    private void validateRecipient(SubjectConfirmationData subjectConfirmationData) {
        if (!callbackUrl.equals(subjectConfirmationData.getRecipient())) {
            throw new InvalidRequestException("SubjectConfirmationData recipient does not match with configured callback URL!");
        }
    }

    private void validateConditions(Conditions conditions) {
        if (conditions == null || conditions.getConditions() == null
                || conditions.getConditions().size() != 1) {
            throw new InvalidRequestException("Assertion must contain exactly 1 Condition!");
        }
        validateNotOnOrAfter(conditions);
        validateNotBefore(conditions);
        validateAudienceRestriction(conditions);
    }

    private void validateNotOnOrAfter(Conditions conditions) {
        DateTime now = new DateTime(conditions.getNotOnOrAfter().getZone());
        if (conditions.getNotOnOrAfter().plusSeconds(acceptedClockSkew).isBefore(now)) {
            throw new InvalidRequestException("SubjectConfirmationData NotOnOrAfter is not valid!");
        }
    }

    private void validateNotBefore(Conditions conditions) {
        DateTime now = new DateTime(conditions.getNotBefore().getZone());
        if (conditions.getNotBefore().minus(acceptedClockSkew).isAfter(now)) {
            throw new InvalidRequestException("Assertion condition NotBefore is not valid!");
        }
    }

    private void validateAudienceRestriction(Conditions conditions) {
        if (conditions.getConditions() == null
                || conditions.getConditions().size() != 1 && conditions.getAudienceRestrictions().size() != 1) {
            throw new InvalidRequestException("Assertion conditions must contain exactly 1 'AudienceRestriction' condition!");
        }
        validateAudiences(conditions.getAudienceRestrictions().get(0).getAudiences());
    }

    private void validateAudiences(List<Audience> audiences) {
        if (audiences == null || audiences.size() < 1 ) {
            throw new InvalidRequestException("Assertion condition's AudienceRestriction must contain at least 1 Audience!");
        }
        for (Audience audience : audiences) {
            if (spEntityID.equals(audience.getAudienceURI())) {
                return;
            }
        }
        throw new InvalidRequestException("Audience does not match with configured SP entity ID!");
    }

    private void validateAuthnStatements(List<AuthnStatement> authnStatements) {
        validateAuthnInstant(authnStatements.get(0).getAuthnInstant());
    }

    private void validateAuthnInstant(DateTime authnInstant) {
        DateTime now = new DateTime(authnInstant.getZone());
        if (now.isAfter(authnInstant.plusSeconds(maxAuthenticationLifetime).plusSeconds(acceptedClockSkew))) {
            throw new InvalidRequestException("AuthnInstant is expired!");
        } else if (now.isBefore(authnInstant.minusSeconds(acceptedClockSkew))) {
            throw new InvalidRequestException("AuthnInstant is in the future!");
        }
    }

}
