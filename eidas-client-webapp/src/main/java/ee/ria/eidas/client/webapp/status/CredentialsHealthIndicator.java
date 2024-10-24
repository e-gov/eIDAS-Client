package ee.ria.eidas.client.webapp.status;

import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.config.EidasClientProperties.HsmProperties;
import ee.ria.eidas.client.config.EidasCredentialsConfiguration.FailedCredentialEvent;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import ee.ria.eidas.client.util.SAMLSigner;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.shared.resolver.CriteriaSet;
import net.shibboleth.shared.resolver.ResolverException;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.EncryptionParameters;
import org.opensaml.xmlsec.EncryptionParametersResolver;
import org.opensaml.xmlsec.config.impl.DefaultSecurityConfigurationBootstrap;
import org.opensaml.xmlsec.criterion.EncryptionConfigurationCriterion;
import org.opensaml.xmlsec.encryption.EncryptedData;
import org.opensaml.xmlsec.encryption.support.DataEncryptionParameters;
import org.opensaml.xmlsec.encryption.support.Encrypter;
import org.opensaml.xmlsec.encryption.support.KeyEncryptionParameters;
import org.opensaml.xmlsec.impl.BasicEncryptionConfiguration;
import org.opensaml.xmlsec.impl.BasicEncryptionParametersResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import se.swedenconnect.opensaml.xmlsec.encryption.support.DecryptionUtils;
import se.swedenconnect.opensaml.xmlsec.encryption.support.Pkcs11Decrypter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.time.Instant.now;
import static java.util.Collections.singletonList;

@Slf4j
@Component
public class CredentialsHealthIndicator extends AbstractHealthIndicator {
    private static final String CERTIFICATE_EXPIRATION_WARNING = "Certificate with serial number '{}' is not valid. Validity period {} - {}";
    private static final String CREDENTIAL_OPERATION_FAILED_WARNING = "Operation with credential '{}' failed";
    private static final String CREDENTIAL_OPERATION_RECOVERY_INFO = "All operations with credentials recovered";

    @Getter
    private final AtomicBoolean credentialsInFailedState = new AtomicBoolean();

    @Getter
    private Clock systemClock;

    @Getter
    @Value("${management.endpoint.heartbeat.credentials.test-interval:60s}")
    private Duration hsmTestInterval;

    @Getter
    private Instant lastTestTime = Instant.now();

    @Autowired
    private BasicX509Credential metadataSigningCredential;

    @Autowired
    private BasicX509Credential authnReqSigningCredential;

    @Autowired
    private BasicX509Credential responseAssertionDecryptionCredential;

    @Autowired
    private final List<BasicX509Credential> allCredentials = new ArrayList<>();

    @Autowired
    private EidasClientProperties eidasClientProperties;

    @Autowired
    private HsmProperties hsmProperties;

    private EncryptionParameters encryptionParameters;

    public CredentialsHealthIndicator() {
        super("Credentials health check failed");
    }

    @PostConstruct
    private void setupIndicator() throws ResolverException {
        systemClock = Clock.systemUTC();
        BasicEncryptionConfiguration basicEncryptionConfiguration = DefaultSecurityConfigurationBootstrap.buildDefaultEncryptionConfiguration();
        basicEncryptionConfiguration.setKeyTransportEncryptionCredentials(singletonList(responseAssertionDecryptionCredential));
        EncryptionConfigurationCriterion criterion = new EncryptionConfigurationCriterion(basicEncryptionConfiguration);
        EncryptionParametersResolver resolver = new BasicEncryptionParametersResolver();
        CriteriaSet criteriaSet = new CriteriaSet(criterion);
        encryptionParameters = resolver.resolveSingle(criteriaSet);
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (isInactiveOrExpiredCertificates()) {
            builder.down().build();
        } else if (isCredentialsInFailedState()) {
            builder.down().build();
        } else {
            builder.up().build();
        }
    }

    public boolean isCredentialsInFailedState() {
        if (hsmProperties.isEnabled()
                && (credentialsInFailedState.get() || getLastTestTime().plus(hsmTestInterval).isBefore(Instant.now(getSystemClock())))) {
            lastTestTime = now();
            if (isValidForSigning(eidasClientProperties.getMetadataSignatureAlgorithm(), metadataSigningCredential)
                    && isValidForSigning(eidasClientProperties.getRequestSignatureAlgorithm(), authnReqSigningCredential)
                    && isValidForDecryption(responseAssertionDecryptionCredential)) {
                if (credentialsInFailedState.get()) {
                    log.info(CREDENTIAL_OPERATION_RECOVERY_INFO);
                }
                credentialsInFailedState.set(false);
                return false;
            } else {
                credentialsInFailedState.set(true);
                return true;
            }
        }
        return false;
    }

    public boolean isValidForSigning(String signingAlgorithmUri, BasicX509Credential credential) {
        try {
            SAMLSigner samlSigner = new SAMLSigner(signingAlgorithmUri, credential);
            EntityDescriptor descriptor = OpenSAMLUtils.buildSAMLObject(EntityDescriptor.class);
            descriptor.setEntityID("test");
            samlSigner.sign(descriptor);
            return true;
        } catch (Exception e) {
            log.error("Signing failed for credential: {}", credential.getEntityId(), e);
            return false;
        }
    }

    public boolean isValidForDecryption(BasicX509Credential credential) {
        try {
            Issuer encryptedObject = (Issuer) XMLObjectSupport.buildXMLObject(Issuer.DEFAULT_ELEMENT_NAME);
            encryptedObject.setValue("credentialsHealthIndicator");
            DataEncryptionParameters encParams = new DataEncryptionParameters(encryptionParameters);
            KeyEncryptionParameters kekParams = new KeyEncryptionParameters(encryptionParameters, "recipient");
            EncryptedData encryptedData = new Encrypter().encryptElement(encryptedObject, encParams, kekParams);
            Pkcs11Decrypter decrypter = new Pkcs11Decrypter(DecryptionUtils.createDecryptionParameters(responseAssertionDecryptionCredential));
            decrypter.setRootInNewDocument(true);
            Issuer decryptedObject = (Issuer) decrypter.decryptData(encryptedData);
            if (decryptedObject.getValue().equals("credentialsHealthIndicator")) {
                return true;
            } else {
                log.error("Encryption/decryption failed for credential: {}. Decrypted value doesn't match initial value.", credential.getEntityId());
                return false;
            }
        } catch (Exception e) {
            log.error("Encryption/decryption failed for credential: {}", credential.getEntityId(), e);
            return false;
        }
    }

    @EventListener
    public void onFailedCredentialEvent(FailedCredentialEvent event) {
        if (!credentialsInFailedState.get()) {
            log.warn(CREDENTIAL_OPERATION_FAILED_WARNING, event.getCredential().getEntityId());
            credentialsInFailedState.set(true);
        }
    }

    public boolean isInactiveOrExpiredCertificates() {
        Instant currentDateTime = Instant.now(getSystemClock());
        return allCredentials.stream()
                .map(BasicX509Credential::getEntityCertificate)
                .filter(es -> currentDateTime.isAfter(es.getNotAfter().toInstant()) || currentDateTime.isBefore(es.getNotBefore().toInstant()))
                .peek(x509 -> log.warn(CERTIFICATE_EXPIRATION_WARNING, x509.getSerialNumber(), x509.getNotBefore(), x509.getNotAfter()))
                .findFirst().isPresent();
    }
}
