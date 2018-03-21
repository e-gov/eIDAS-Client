package ee.ria.eidas.client.config;

import ee.ria.eidas.client.AuthInitiationService;
import ee.ria.eidas.client.AuthResponseService;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.metadata.IDPMetadataResolver;
import ee.ria.eidas.client.metadata.SPMetadataGenerator;
import ee.ria.eidas.client.session.RequestSessionService;
import ee.ria.eidas.client.session.RequestSessionServiceImpl;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.Criterion;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.common.xml.SAMLSchemaBuilder;
import org.opensaml.saml.metadata.resolver.impl.AbstractReloadingMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.PredicateRoleDescriptorResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.saml.security.impl.MetadataCredentialResolver;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.CredentialSupport;
import org.opensaml.security.credential.UsageType;
import org.opensaml.security.credential.impl.KeyStoreCredentialResolver;
import org.opensaml.security.credential.impl.StaticCredentialResolver;
import org.opensaml.security.x509.X509Credential;
import org.opensaml.xmlsec.config.DefaultSecurityConfigurationBootstrap;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.impl.X509CertificateImpl;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.xml.sax.SAXException;

import javax.xml.validation.Schema;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

@Configuration
@EnableConfigurationProperties({
        EidasClientProperties.class
})
@EnableScheduling
public class EidasClientConfiguration {

    @Autowired
    private EidasClientProperties eidasClientProperties;

    @Autowired
    private ResourceLoader resourceLoader;

    @Bean
    public KeyStore samlKeystore() {
        try {
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            Resource resource = resourceLoader.getResource(eidasClientProperties.getKeystore());
            keystore.load(resource.getInputStream(), eidasClientProperties.getKeystorePass().toCharArray());
            return keystore;
        } catch (Exception e) {
            throw new EidasClientException("Something went wrong reading the keystore", e);
        }
    }

    @Bean
    public Schema samlSchema() {
        try {
            return new SAMLSchemaBuilder(SAMLSchemaBuilder.SAML1Version.SAML_11).getSAMLSchema();
        } catch (SAXException e) {
            throw new EidasClientException("Failed to read SAML schemas!", e);
        }
    }

    @Bean
    public Credential metadataSigningCredential(KeyStore keyStore) {
        return getCredential(
                keyStore,
                eidasClientProperties.getMetadataSigningKeyId(),
                eidasClientProperties.getMetadataSigningKeyPass());
    }

    @Bean
    public Credential authnReqSigningCredential(KeyStore keyStore) {
        return getCredential(
                keyStore,
                eidasClientProperties.getRequestSigningKeyId(),
                eidasClientProperties.getRequestSigningKeyPass());
    }

    @Bean
    public Credential responseAssertionDecryptionCredential(KeyStore keyStore) {
        return getCredential(
                keyStore,
                eidasClientProperties.getResponseDecryptionKeyId(),
                eidasClientProperties.getResponseDecryptionKeyPass());
    }

    @Bean
    public SPMetadataGenerator metadataGenerator(@Qualifier("metadataSigningCredential") Credential metadataSigningCredential, @Qualifier("authnReqSigningCredential") Credential authnReqSigningCredential, @Qualifier("responseAssertionDecryptionCredential") Credential responseAssertionDecryptionCredential) {
        return new SPMetadataGenerator(eidasClientProperties, metadataSigningCredential, authnReqSigningCredential, responseAssertionDecryptionCredential);
    }

    @Bean
    public ExplicitKeySignatureTrustEngine idpMetadataSignatureTrustEngine(KeyStore keyStore) {
        try {
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(eidasClientProperties.getIdpMetadataSigningCertificateKeyId());
            if (cert == null) {
                throw new EidasClientException("It seems you are missing a certificate with alias '" + eidasClientProperties.getIdpMetadataSigningCertificateKeyId() + "' in your " + eidasClientProperties.getKeystore() + " keystore. We need it in order to verify IDP metadata's signature.");
            }
            X509Credential switchCred = CredentialSupport.getSimpleCredential(cert, null);
            StaticCredentialResolver switchCredResolver = new StaticCredentialResolver(switchCred);
            return new ExplicitKeySignatureTrustEngine(switchCredResolver, DefaultSecurityConfigurationBootstrap.buildBasicInlineKeyInfoCredentialResolver());
        } catch (KeyStoreException e) {
            throw new EidasClientException("Error initializing. Cannot get IDP metadata trusted certificate", e);
        }
    }

    @Bean
    public ExplicitKeySignatureTrustEngine idpMetadataSignatureTrustEngine(IDPMetadataResolver idpMetadataResolver) {
        MetadataCredentialResolver metadataCredentialResolver = new MetadataCredentialResolver();
        PredicateRoleDescriptorResolver roleResolver = new PredicateRoleDescriptorResolver(idpMetadataResolver.resolve());

        KeyInfoCredentialResolver keyResolver = DefaultSecurityConfigurationBootstrap.buildBasicInlineKeyInfoCredentialResolver();

        metadataCredentialResolver.setKeyInfoCredentialResolver(keyResolver);
        metadataCredentialResolver.setRoleDescriptorResolver(roleResolver);

        try {
            metadataCredentialResolver.initialize();
            roleResolver.initialize();
        } catch (final ComponentInitializationException e) {
            throw new EidasClientException("Error initializing metadataCredentialResolver", e);
        }

        return new ExplicitKeySignatureTrustEngine(metadataCredentialResolver, keyResolver);
    }

    @Bean
    public IDPMetadataResolver idpMetadataResolver(@Qualifier("idpMetadataSignatureTrustEngine") ExplicitKeySignatureTrustEngine metadataSignatureTrustEngine) {
        return new IDPMetadataResolver(eidasClientProperties.getIdpMetadataUrl(), metadataSignatureTrustEngine);
    }

    @Bean
    public ExplicitKeySignatureTrustEngine responseSignatureTrustEngine(IDPMetadataResolver idpMetadataResolver) {
        X509Certificate cert = getResponseSigningCertificate(idpMetadataResolver);
        X509Credential switchCred = CredentialSupport.getSimpleCredential(cert, null);
        StaticCredentialResolver switchCredResolver = new StaticCredentialResolver(switchCred);
        return new ExplicitKeySignatureTrustEngine(switchCredResolver, DefaultSecurityConfigurationBootstrap.buildBasicInlineKeyInfoCredentialResolver());
    }

    @Bean
    public SingleSignOnService singleSignOnService(IDPMetadataResolver idpMetadataResolver) {
        try {
            AbstractReloadingMetadataResolver metadataResolver = idpMetadataResolver.resolve();
            CriteriaSet criteriaSet = new CriteriaSet(new EntityIdCriterion(eidasClientProperties.getIdpMetadataUrl()));
            EntityDescriptor entityDescriptor = metadataResolver.resolveSingle(criteriaSet);
            if (entityDescriptor == null) {
                throw new EidasClientException("Could not find a valid EntityDescriptor in your IDP metadata! ");
            }
            for (SingleSignOnService ssoService : entityDescriptor.getIDPSSODescriptor(SAMLConstants.SAML20P_NS).getSingleSignOnServices()) {
                if (ssoService.getBinding().equals(SAMLConstants.SAML2_POST_BINDING_URI)) {
                    return ssoService;
                }
            }
        } catch (final ResolverException e) {
            throw new EidasClientException("Error initializing IDP metadata", e);
        }

        throw new EidasClientException("Could not find a valid SAML2 POST BINDING from IDP metadata!");
    }

    @Bean
    public RequestSessionService requestSessionService() {
        return new RequestSessionServiceImpl(eidasClientProperties);
    }

    @Bean
    public AuthInitiationService authInitiationService(RequestSessionService requestSessionService, @Qualifier("authnReqSigningCredential") Credential signingCredential, SingleSignOnService singleSignOnService) {
        return new AuthInitiationService(requestSessionService, signingCredential, eidasClientProperties, singleSignOnService);
    }

    @Bean
    public AuthResponseService authResponseService(
            RequestSessionService requestSessionService,
            @Qualifier("responseSignatureTrustEngine") ExplicitKeySignatureTrustEngine explicitKeySignatureTrustEngine,
            @Qualifier("responseAssertionDecryptionCredential") Credential responseAssertionDecryptionCredential, Schema samlSchema) {
        return new AuthResponseService(requestSessionService, eidasClientProperties, explicitKeySignatureTrustEngine, responseAssertionDecryptionCredential, samlSchema);
    }

    private Credential getCredential(KeyStore keystore, String keyPairId, String privateKeyPass) {
        try {
            Map<String, String> passwordMap = new HashMap<>();
            passwordMap.put(keyPairId, privateKeyPass);
            KeyStoreCredentialResolver resolver = new KeyStoreCredentialResolver(keystore, passwordMap);

            Criterion criterion = new EntityIdCriterion(keyPairId);
            CriteriaSet criteriaSet = new CriteriaSet();
            criteriaSet.add(criterion);

            return resolver.resolveSingle(criteriaSet);
        } catch (ResolverException e) {
            throw new RuntimeException("Something went wrong reading credentials", e);
        }
    }

    private X509Certificate getResponseSigningCertificate(IDPMetadataResolver idpMetadataResolver) {
        try {
            List<KeyDescriptor> idpSsoKeyDescriptors = idpMetadataResolver.resolve().iterator().next().getIDPSSODescriptor(SAMLConstants.SAML20P_NS).getKeyDescriptors();
            Optional<KeyDescriptor> matchingDescriptor = idpSsoKeyDescriptors.stream().
                    filter(d -> d.getUse() == UsageType.SIGNING).findFirst();
            KeyDescriptor signingDescriptor = matchingDescriptor.orElse(null);
            if (signingDescriptor == null) {
                throw new EidasClientException("Could not find signing descriptor from IDP metadata");
            }
            X509CertificateImpl certificate = (X509CertificateImpl) signingDescriptor.getKeyInfo().getX509Datas().get(0).getX509Certificates().get(0);
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream certInputStream = new ByteArrayInputStream(Base64.getDecoder().decode(certificate.getValue().replaceAll("\n", "")));
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(certInputStream);
            return cert;
        } catch (CertificateException e) {
            throw new EidasClientException("Error initializing. Cannot get IDP metadata trusted certificate", e);
        }
    }
}
