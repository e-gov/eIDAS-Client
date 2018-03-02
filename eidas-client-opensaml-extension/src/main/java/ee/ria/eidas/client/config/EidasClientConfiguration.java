package ee.ria.eidas.client.config;

import ee.ria.eidas.client.authnrequest.EidasAuthenticationService;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.metadata.IDPMetadataResolver;
import ee.ria.eidas.client.metadata.SPMetadataGenerator;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.Criterion;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.PredicateRoleDescriptorResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.saml.security.impl.MetadataCredentialResolver;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.CredentialSupport;
import org.opensaml.security.credential.impl.KeyStoreCredentialResolver;
import org.opensaml.security.credential.impl.StaticCredentialResolver;
import org.opensaml.security.x509.X509Credential;
import org.opensaml.xmlsec.config.DefaultSecurityConfigurationBootstrap;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties({
        EidasClientProperties.class
})
public class EidasClientConfiguration {

    @Autowired
    private EidasClientProperties eidasClientProperties;

    @Autowired
    private ResourceLoader resourceLoader;

    @Bean
    public SPMetadataGenerator metadataGenerator(@Qualifier("metadataSigningCredential") Credential metadataSigningCredential, @Qualifier("authnReqSigningCredential") Credential authnReqSigningCredential, @Qualifier("responseAssertionDecryptionCredential") Credential responseAssertionDecryptionCredential) {
        return new SPMetadataGenerator(eidasClientProperties, metadataSigningCredential, authnReqSigningCredential, responseAssertionDecryptionCredential);
    }

    @Bean
    public IDPMetadataResolver idpMetadataResolver(@Qualifier("metadataSignatureTrustEngine") ExplicitKeySignatureTrustEngine metadataSignatureTrustEngine)  {
        try {
            InputStream idpMetadata = resourceLoader.getResource(eidasClientProperties.getIdpMetadataUrl()).getInputStream();
            return new IDPMetadataResolver(idpMetadata, metadataSignatureTrustEngine);
        } catch (IOException e) {
            throw new IllegalStateException("Connection problems? Could not read IDP metadata from the following URL: " + eidasClientProperties.getIdpMetadataUrl(), e);
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
    public Credential idpMetadataSigningCredential(KeyStore keyStore) {
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
    public SingleSignOnService singleSignOnService(IDPMetadataResolver idpMetadataResolver) {
        try {
            MetadataResolver metadataResolver = idpMetadataResolver.resolve();
            CriteriaSet criteriaSet = new CriteriaSet(new EntityIdCriterion(idpMetadataResolver.getEntityId()));
            EntityDescriptor entityDescriptor = metadataResolver.resolveSingle(criteriaSet);
            if (entityDescriptor == null) {
                throw new IllegalStateException("Could not find a valid EntityDescriptor in your IDP metadata! ");
            }
            for ( SingleSignOnService ssoService : entityDescriptor.getIDPSSODescriptor(SAMLConstants.SAML20P_NS).getSingleSignOnServices() ) {
                if ( ssoService.getBinding().equals(SAMLConstants.SAML2_POST_BINDING_URI) ) {
                    return ssoService;
                }
            }
        } catch (final ResolverException e) {
            throw new IllegalStateException("Error initializing idp metadata", e);
        }

        throw new EidasClientException("Could not find a valid SAML2 POST BINDING from IDP metadata!");
    }

    @Bean
    public EidasAuthenticationService eidasAuthProtectedUrlFilterRegistration(
            @Qualifier("authnReqSigningCredential") Credential signingCredential,
            SingleSignOnService singleSignOnService) {
        return new EidasAuthenticationService(signingCredential, eidasClientProperties, singleSignOnService);
    }

    @Bean
    public ExplicitKeySignatureTrustEngine metadataSignatureTrustEngine(KeyStore keyStore) {
        try {
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(eidasClientProperties.getIdpMetadataSigningCertificateKeyId());
            if (cert == null)
                throw new IllegalStateException("It seems you are missing a certificate with alias '" + eidasClientProperties.getIdpMetadataSigningCertificateKeyId() + "' in your " + eidasClientProperties.getKeystore() + " keystore. We need it in order to verify IDP metadata's signature.");

            X509Credential switchCred = CredentialSupport.getSimpleCredential(cert, null);
            StaticCredentialResolver switchCredResolver = new StaticCredentialResolver(switchCred);
            return new ExplicitKeySignatureTrustEngine(switchCredResolver, DefaultSecurityConfigurationBootstrap.buildBasicInlineKeyInfoCredentialResolver());
        } catch (KeyStoreException e) {
            throw new EidasClientException("Error initializing. Cannot get IDP metadata trusted certificate" ,e);
        }
    }

    @Bean
    public ExplicitKeySignatureTrustEngine responseSignatureTrustEngine(KeyStore keyStore) {
        try {
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(eidasClientProperties.getResponseSigningCertificateKeyId());
            if (cert == null)
                throw new IllegalStateException("It seems you are missing a certificate with alias '" + eidasClientProperties.getResponseSigningCertificateKeyId() + "' in your " + eidasClientProperties.getKeystore() + " keystore. We need it in order to verify SAML response's asserion signature.");

            X509Credential switchCred = CredentialSupport.getSimpleCredential(cert, null);
            StaticCredentialResolver switchCredResolver = new StaticCredentialResolver(switchCred);
            return new ExplicitKeySignatureTrustEngine(switchCredResolver, DefaultSecurityConfigurationBootstrap.buildBasicInlineKeyInfoCredentialResolver());
        } catch (KeyStoreException e) {
            throw new EidasClientException("Error initializing. Cannot get IDP metadata trusted certificate" ,e);
        }
    }

    @Bean
    public ExplicitKeySignatureTrustEngine idpMetadataSignatureTrustEngine(IDPMetadataResolver IDPMetadataResolver) {
        MetadataCredentialResolver metadataCredentialResolver = new MetadataCredentialResolver();
        PredicateRoleDescriptorResolver roleResolver = new PredicateRoleDescriptorResolver(IDPMetadataResolver.resolve());

        KeyInfoCredentialResolver keyResolver = DefaultSecurityConfigurationBootstrap.buildBasicInlineKeyInfoCredentialResolver();

        metadataCredentialResolver.setKeyInfoCredentialResolver(keyResolver);
        metadataCredentialResolver.setRoleDescriptorResolver(roleResolver);

        try {
            metadataCredentialResolver.initialize();
            roleResolver.initialize();
        } catch (final ComponentInitializationException e) {
            throw new EidasClientException("Error initializing metadataCredentialResolver" ,e);
        }

        return new ExplicitKeySignatureTrustEngine(metadataCredentialResolver, keyResolver);
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
}
