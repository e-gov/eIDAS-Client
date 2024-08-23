package ee.ria.eidas.client.config;

import ee.ria.eidas.client.config.EidasClientProperties.HsmProperties;
import ee.ria.eidas.client.exception.EidasClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.x509.BasicX509Credential;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Optional;

import static java.lang.String.format;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EidasCredentialsConfiguration {
    private final EidasClientProperties eidasClientProperties;
    private final HsmProperties hsmProperties;

    @Bean
    public KeyStore softwareKeystore(ResourceLoader resourceLoader) {
        try {
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            Resource resource = resourceLoader.getResource(eidasClientProperties.getKeystore());
            keystore.load(resource.getInputStream(), eidasClientProperties.getKeystorePass().toCharArray());
            return keystore;
        } catch (Exception e) {
            throw new EidasClientException("Something went wrong reading the software keystore", e);
        }
    }

    @Bean
    @ConditionalOnProperty(name = "eidas.client.hsm.enabled", havingValue = "true")
    public KeyStore hardwareKeystore(HsmProperties hsmProperties) {
        try {
            log.info("Hardware security module enabled. Slot/slot index: {}/{}, Library: {}",
                    hsmProperties.getSlot(), hsmProperties.getSlotListIndex(), hsmProperties.getLibrary());
            Provider provider = Security.getProvider("SunPKCS11").configure(hsmProperties.toString());
            Security.addProvider(provider);
            KeyStore keyStore = KeyStore.getInstance("PKCS11", provider);
            keyStore.load(null, hsmProperties.getPin().toCharArray());
            return keyStore;
        } catch (Exception e) {
            throw new EidasClientException("Something went wrong reading the hardware keystore", e);
        }
    }

    @Bean
    public X509Certificate idpMetadataSigningCertificate(KeyStore softwareKeystore) throws KeyStoreException {
        X509Certificate cert = (X509Certificate) softwareKeystore.getCertificate(eidasClientProperties.getIdpMetadataSigningCertificateKeyId());
        if (cert == null) {
            String msg = "It seems you are missing a certificate with alias '%s' in your %s keystore. We need it in order to verify IDP metadata's signature.";
            throw new EidasClientException(format(msg, eidasClientProperties.getIdpMetadataSigningCertificateKeyId(), eidasClientProperties.getKeystore()));
        }
        return cert;
    }

    @Bean
    public BasicX509Credential metadataSigningCredential(KeyStore softwareKeystore, Optional<KeyStore> hardwareKeystore) throws Exception {
        String password = hsmProperties.isEnabled() ? hsmProperties.getPin() : eidasClientProperties.getMetadataSigningKeyPass();
        return getCredential(softwareKeystore, hardwareKeystore, eidasClientProperties.getMetadataSigningKeyId(), password);
    }

    @Bean
    public BasicX509Credential authnReqSigningCredential(KeyStore softwareKeystore, Optional<KeyStore> hardwareKeystore) throws Exception {
        String password = hsmProperties.isEnabled() ? hsmProperties.getPin() : eidasClientProperties.getRequestSigningKeyPass();
        return getCredential(softwareKeystore, hardwareKeystore, eidasClientProperties.getRequestSigningKeyId(), password);
    }

    @Bean
    public BasicX509Credential responseAssertionDecryptionCredential(KeyStore softwareKeystore, Optional<KeyStore> hardwareKeystore) throws Exception {
        String password = hsmProperties.isEnabled() ? hsmProperties.getPin() : eidasClientProperties.getResponseDecryptionKeyPass();
        return getCredential(softwareKeystore, hardwareKeystore, eidasClientProperties.getResponseDecryptionKeyId(), password);
    }

    private BasicX509Credential getCredential(KeyStore softwareKeystore, Optional<KeyStore> hardwareKeystore, String alias, String password) throws Exception {
        PrivateKey privateKey = getPrivateKey(softwareKeystore, hardwareKeystore, alias, password);
        if (privateKey == null) {
            throw new EidasClientException(format("Private key with alias '%s' not found in keystore", alias));
        }
        X509Certificate x509Cert = getCertificate(softwareKeystore, hardwareKeystore, alias);
        BasicX509Credential basicX509Credential = new BasicX509Credential(x509Cert, privateKey);
        basicX509Credential.setEntityId(alias);
        return basicX509Credential;
    }

    private PrivateKey getPrivateKey(KeyStore softwareKeystore, Optional<KeyStore> hardwareKeystore, String alias, String password) throws Exception {
        return hardwareKeystore.isPresent()
            ? (PrivateKey) hardwareKeystore.get().getKey(alias, password.toCharArray())
            : (PrivateKey) softwareKeystore.getKey(alias, password.toCharArray());
    }

    private X509Certificate getCertificate(KeyStore softwareKeystore, Optional<KeyStore> hardwareKeystore, String alias) throws KeyStoreException {
        boolean certificatesFromHsm = hardwareKeystore.isPresent() && hsmProperties.isCertificatesFromHsm();
        return certificatesFromHsm
            ? (X509Certificate) hardwareKeystore.get().getCertificate(alias)
            : (X509Certificate) softwareKeystore.getCertificate(alias);
    }

    @Getter
    public static class FailedCredentialEvent extends ApplicationEvent {
        private final Credential credential;

        public FailedCredentialEvent(Credential credential) {
            super(credential);
            this.credential = credential;
        }
    }
}
