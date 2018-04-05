package ee.ria.eidas.client.assertion;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.SAMLAssertionException;
import ee.ria.eidas.client.session.RequestSession;
import ee.ria.eidas.client.session.RequestSessionService;
import org.joda.time.DateTime;
import org.opensaml.saml.saml2.core.*;

import java.util.Arrays;
import java.util.List;

public class AssertionValidator {

    private int acceptedClockSkew;
    private String idpMetadataUrl;
    private String callbackUrl;
    private String spEntityID;
    private int maxAuthenticationLifetime;

    private RequestSessionService requestSessionService;

    public AssertionValidator(EidasClientProperties properties, RequestSessionService requestSessionService) {
        this.acceptedClockSkew = properties.getAcceptedClockSkew();
        this.maxAuthenticationLifetime = properties.getMaximumAuthenticationLifetime();
        this.idpMetadataUrl = properties.getIdpMetadataUrl();
        this.callbackUrl = properties.getCallbackUrl();
        this.spEntityID = properties.getSpEntityId();

        this.requestSessionService = requestSessionService;
    }

    public void validate(Assertion assertion) {
        validateAgainstRequestSession(assertion);
        validateIssueInstant(assertion);
        validateIssuer(assertion.getIssuer());
        validateSubject(assertion.getSubject());
        validateConditions(assertion.getConditions());
        validateAuthnStatements(assertion.getAuthnStatements());
    }

    private synchronized void validateAgainstRequestSession(Assertion assertion) {
        String requestID = assertion.getSubject().getSubjectConfirmations().get(0).getSubjectConfirmationData().getInResponseTo();
        RequestSession requestSession = requestSessionService.getRequestSession(requestID);
        if (requestSession == null) {
            throw new SAMLAssertionException("No corresponding SAML request session found for the given response assertion!");
        } else {
            requestSessionService.removeRequestSession(requestID);
            DateTime now = new DateTime(requestSession.getIssueInstant().getZone());
            if (now.isAfter(requestSession.getIssueInstant().plusSeconds(maxAuthenticationLifetime).plusSeconds(acceptedClockSkew))) {
                throw new SAMLAssertionException("Request session with ID " + requestID + " has expired!");
            }
        }

        if (assertion.getAuthnStatements() == null || assertion.getAuthnStatements().size() != 1 ) {
            throw new SAMLAssertionException("Assertion must contain exactly 1 AuthnStatement!");
        }
        if (assertion.getAuthnStatements().get(0).getAuthnContext().getAuthnContextClassRef() == null) {
            throw new SAMLAssertionException("Authncontext must contain AuthnContextClassRef!");
        }
        boolean isReturnedLoaValid = false;
        for (AssuranceLevel loa : AssuranceLevel.values()) {
            if (loa.getUri().equalsIgnoreCase(assertion.getAuthnStatements().get(0).getAuthnContext().getAuthnContextClassRef().getAuthnContextClassRef())
                    &&  loa.getLevel() >= requestSession.getLoa().getLevel()) {
                isReturnedLoaValid = true;
            }
        }
        if (!isReturnedLoaValid) {
            throw new SAMLAssertionException("AuthnContextClassRef is not greater or equal to the request level of assurance!");
        }
    }

    private void validateIssueInstant(Assertion assertion) {
        DateTime now = new DateTime(assertion.getIssueInstant().getZone());
        if (assertion.getIssueInstant().isAfter(now.plusSeconds(acceptedClockSkew)) ||
                assertion.getIssueInstant().isBefore(now.minusSeconds(acceptedClockSkew))) {
            throw new SAMLAssertionException("Assertion issue instant is too old or in the future!");
        }
    }

    private void validateIssuer(Issuer issuer) {
        if (issuer == null) {
            throw new SAMLAssertionException("Assertion is missing issuer!");
        } else if (issuer.getValue() == null || !issuer.getValue().equals(idpMetadataUrl)) {
            throw new SAMLAssertionException("Assertion issuer's value is not equal to the configured IDP metadata url!");
        } else if (issuer.getFormat() == null || !NameIDType.ENTITY.equals(issuer.getFormat())) {
            throw new SAMLAssertionException("Assertion issuer's format must equal to: " + NameIDType.ENTITY + "!");
        }
    }

    private void validateSubject(Subject subject) {
        if (subject == null) {
            throw new SAMLAssertionException("Assertion is missing subject!");
        }
        validateSubjectNameId(subject.getNameID());
        validateSubjectConfirmation(subject.getSubjectConfirmations());
    }

    private void validateSubjectNameId(NameID nameID) {
        if (nameID == null) {
            throw new SAMLAssertionException("Assertion subject is missing nameID!");
        }
        List<String> validNameIDFormats = Arrays.asList(NameIDType.UNSPECIFIED , NameIDType.TRANSIENT, NameIDType.PERSISTENT);
        if (!validNameIDFormats.contains(nameID.getFormat())) {
            throw new SAMLAssertionException("Assertion's subject name ID format is not equal to one of the following: " + validNameIDFormats);
        }
    }

    private void validateSubjectConfirmation(List<SubjectConfirmation> subjectConfirmations) {
        if (subjectConfirmations == null || subjectConfirmations.size() != 1) {
            throw new SAMLAssertionException("Assertion subject must contain exactly 1 SubjectConfirmation!");
        }
        if (!SubjectConfirmation.METHOD_BEARER.equals(subjectConfirmations.get(0).getMethod())) {
            throw new SAMLAssertionException("Assertion SubjectConfirmation must equal to: " + SubjectConfirmation.METHOD_BEARER + "!");
        }

        SubjectConfirmationData subjectConfirmationData = subjectConfirmations.get(0).getSubjectConfirmationData();
        if (subjectConfirmationData == null) {
            throw new SAMLAssertionException("Assertion's subject SubjectConfirmation!");
        }
        validateNotOnOrAfter(subjectConfirmationData);
        validateRecipient(subjectConfirmationData);
    }

    private void validateNotOnOrAfter(SubjectConfirmationData subjectConfirmationData) {
        DateTime now = new DateTime(subjectConfirmationData.getNotOnOrAfter().getZone());
        if (subjectConfirmationData.getNotOnOrAfter().plusSeconds(acceptedClockSkew).isBefore(now)) {
            throw new SAMLAssertionException("SubjectConfirmationData NotOnOrAfter is not valid!");
        }
    }

    private void validateRecipient(SubjectConfirmationData subjectConfirmationData) {
        if (!callbackUrl.equals(subjectConfirmationData.getRecipient())) {
            throw new SAMLAssertionException("SubjectConfirmationData recipient does not match with configured callback URL!");
        }
    }

    private void validateConditions(Conditions conditions) {
        if (conditions == null || conditions.getConditions() == null
                || conditions.getConditions().size() != 1) {
            throw new SAMLAssertionException("Assertion must contain exactly 1 Condition!");
        }
        validateNotOnOrAfter(conditions);
        validateNotBefore(conditions);
        validateAudienceRestriction(conditions);
    }

    private void validateNotOnOrAfter(Conditions conditions) {
        DateTime now = new DateTime(conditions.getNotOnOrAfter().getZone());
        if (conditions.getNotOnOrAfter().plusSeconds(acceptedClockSkew).isBefore(now)) {
            throw new SAMLAssertionException("SubjectConfirmationData NotOnOrAfter is not valid!");
        }
    }

    private void validateNotBefore(Conditions conditions) {
        DateTime now = new DateTime(conditions.getNotBefore().getZone());
        if (conditions.getNotBefore().minus(acceptedClockSkew).isAfter(now)) {
            throw new SAMLAssertionException("Assertion condition NotBefore is not valid!");
        }
    }

    private void validateAudienceRestriction(Conditions conditions) {
        if (conditions.getConditions() == null
                || conditions.getConditions().size() != 1 && conditions.getAudienceRestrictions().size() != 1) {
            throw new SAMLAssertionException("Assertion conditions must contain exactly 1 'AudienceRestriction' condition!");
        }
        validateAudiences(conditions.getAudienceRestrictions().get(0).getAudiences());
    }

    private void validateAudiences(List<Audience> audiences) {
        if (audiences == null || audiences.size() < 1 ) {
            throw new SAMLAssertionException("Assertion condition's AudienceRestriction must contain at least 1 Audience!");
        }
        for (Audience audience : audiences) {
            if (spEntityID.equals(audience.getAudienceURI())) {
                return;
            }
        }
        throw new SAMLAssertionException("Audience does not match with configured SP entity ID!");
    }

    private void validateAuthnStatements(List<AuthnStatement> authnStatements) {
        validateAuthnInstant(authnStatements.get(0).getAuthnInstant());
    }

    private void validateAuthnInstant(DateTime authnInstant) {
        DateTime now = new DateTime(authnInstant.getZone());
        if (now.isAfter(authnInstant.plusSeconds(maxAuthenticationLifetime).plusSeconds(acceptedClockSkew))) {
            throw new SAMLAssertionException("AuthnInstant is expired!");
        } else if (now.isBefore(authnInstant.minusSeconds(acceptedClockSkew))) {
            throw new SAMLAssertionException("AuthnInstant is in the future!");
        }
    }

}
