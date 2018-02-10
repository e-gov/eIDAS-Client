package ee.ria.eidas.config;

import ee.ria.eidas.AccessFilter;
import ee.ria.eidas.AssertionConsumerServlet;
import ee.ria.eidas.metadata.IdpMetadataResolver;
import ee.ria.eidas.metadata.SpMetadataGenerator;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.Criterion;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.BasicRoleDescriptorResolver;
import org.opensaml.saml.security.impl.MetadataCredentialResolver;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.impl.KeyStoreCredentialResolver;
import org.opensaml.xmlsec.config.DefaultSecurityConfigurationBootstrap;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.pac4j.saml.exceptions.SAMLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

@Configuration
@ComponentScan("ee.ria.eidas")
@EnableConfigurationProperties({
        EidasClientProperties.class
})
public class EidasClientConfiguration {

    @Autowired
    private EidasClientProperties eidasClientProperties;

    @Autowired
    private ResourceLoader resourceLoader;

    @Bean
    public SpMetadataGenerator metadataGenerator(@Qualifier("metadataSigningCredential") Credential metadataSigningCredential, @Qualifier("authnReqSigningCredential") Credential authnReqSigningCredential, @Qualifier("responseAssertionDecryptionCredential") Credential responseAssertionDecryptionCredential) {
        return new SpMetadataGenerator(eidasClientProperties, metadataSigningCredential, authnReqSigningCredential, responseAssertionDecryptionCredential);
    }

    @Bean
    public IdpMetadataResolver idpMetadataResolver() {
        return new IdpMetadataResolver(resourceLoader.getResource(eidasClientProperties.getIdpMetadataUrl()));
    }

    @Bean
    public FilterRegistrationBean samlProtectedUrlFilterRegistration(@Qualifier("authnReqSigningCredential") Credential signingCredential) {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(new AccessFilter(signingCredential, eidasClientProperties));
        registration.addUrlPatterns(eidasClientProperties.getLoginUrl());
        registration.setName("samlFilter");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public ServletRegistrationBean samlAssertionConsumerServlet(ExplicitKeySignatureTrustEngine explicitKeySignatureTrustEngine, @Qualifier("responseAssertionDecryptionCredential") Credential responseAssertionDecryptionCredential) {
        ServletRegistrationBean bean = new ServletRegistrationBean(
                new AssertionConsumerServlet(eidasClientProperties, explicitKeySignatureTrustEngine, responseAssertionDecryptionCredential), eidasClientProperties.getSamlAssertionConsumerUrl());
        bean.setLoadOnStartup(1);
        return bean;
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
            throw new RuntimeException("Something went wrong reading the keystore", e);
        }
    }

    @Bean
    public ExplicitKeySignatureTrustEngine explicitKeySignatureTrustEngine(IdpMetadataResolver idpMetadataResolver) {
        final MetadataCredentialResolver metadataCredentialResolver = new MetadataCredentialResolver();
        final BasicRoleDescriptorResolver roleResolver = new BasicRoleDescriptorResolver(idpMetadataResolver.resolve());

        final KeyInfoCredentialResolver keyResolver =
                DefaultSecurityConfigurationBootstrap.buildBasicInlineKeyInfoCredentialResolver();

        metadataCredentialResolver.setKeyInfoCredentialResolver(keyResolver);
        metadataCredentialResolver.setRoleDescriptorResolver(roleResolver);

        try {
            metadataCredentialResolver.initialize();
            roleResolver.initialize();
        } catch (final ComponentInitializationException e) {
            throw new SAMLException(e);
        }

        return new ExplicitKeySignatureTrustEngine(metadataCredentialResolver, keyResolver);
    }

    private Credential getCredential(KeyStore keystore, String keyPairId, String privateKeyPass) {
        try {
            Map<String, String> passwordMap = new HashMap<String, String>();
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
