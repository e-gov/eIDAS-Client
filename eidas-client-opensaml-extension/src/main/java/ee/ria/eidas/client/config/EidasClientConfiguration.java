package ee.ria.eidas.client.config;

import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.metadata.IDPMetadataResolver;
import ee.ria.eidas.client.metadata.SPMetadataGenerator;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.Criterion;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.impl.PredicateRoleDescriptorResolver;
import org.opensaml.saml.security.impl.MetadataCredentialResolver;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.impl.KeyStoreCredentialResolver;
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
import java.security.KeyStore;
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
    public IDPMetadataResolver idpMetadataResolver() throws IOException {
        return new IDPMetadataResolver(resourceLoader.getResource(eidasClientProperties.getIdpMetadataUrl()).getInputStream());
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
    public ExplicitKeySignatureTrustEngine explicitKeySignatureTrustEngine(IDPMetadataResolver IDPMetadataResolver) {
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
