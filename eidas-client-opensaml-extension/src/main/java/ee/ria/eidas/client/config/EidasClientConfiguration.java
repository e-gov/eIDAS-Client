package ee.ria.eidas.client.config;

import ee.ria.eidas.client.AuthInitiationService;
import ee.ria.eidas.client.AuthResponseService;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.metadata.IDPMetadataResolver;
import ee.ria.eidas.client.metadata.SPMetadataGenerator;
import ee.ria.eidas.client.session.LocalRequestSessionServiceImpl;
import ee.ria.eidas.client.session.RequestSessionService;
import net.shibboleth.shared.component.ComponentInitializationException;
import org.opensaml.saml.common.xml.SAMLSchemaBuilder;
import org.opensaml.saml.metadata.resolver.impl.PredicateRoleDescriptorResolver;
import org.opensaml.saml.security.impl.MetadataCredentialResolver;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.CredentialSupport;
import org.opensaml.security.credential.impl.StaticCredentialResolver;
import org.opensaml.security.x509.X509Credential;
import org.opensaml.xmlsec.config.impl.DefaultSecurityConfigurationBootstrap;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.xml.sax.SAXException;

import javax.xml.validation.Schema;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;

@ConfigurationPropertiesScan
@EnableScheduling
// TODO Investigate if two bean methos with same name (idpMetadataSignatureTrustEngine) are really necessary or can be renamed to make situation clearer. Currently renaming breaks OpenSAML signature validation certificate loading.
@Configuration(enforceUniqueMethods = false)
public class EidasClientConfiguration {

    @Autowired
    private EidasClientProperties eidasClientProperties;

    @Bean
    public Schema samlSchema() {
        try {
            return new SAMLSchemaBuilder(SAMLSchemaBuilder.SAML1Version.SAML_11).getSAMLSchema();
        } catch (SAXException e) {
            throw new EidasClientException("Failed to read SAML schemas!", e);
        }
    }

    @Bean
    public SPMetadataGenerator metadataGenerator(@Qualifier("metadataSigningCredential") Credential metadataSigningCredential,
                                                 @Qualifier("authnReqSigningCredential") Credential authnReqSigningCredential,
                                                 @Qualifier("responseAssertionDecryptionCredential") Credential responseAssertionDecryptionCredential,
                                                 ApplicationEventPublisher applicationEventPublisher) {
        return new SPMetadataGenerator(eidasClientProperties, metadataSigningCredential, authnReqSigningCredential, responseAssertionDecryptionCredential, applicationEventPublisher);
    }

    @Bean
    public ExplicitKeySignatureTrustEngine idpMetadataSignatureTrustEngine(KeyStore softwareKeystore) {
        try {
            X509Certificate cert = (X509Certificate) softwareKeystore.getCertificate(eidasClientProperties.getIdpMetadataSigningCertificateKeyId());
            if (cert == null) {
                throw new EidasClientException("It seems you are missing a certificate with alias '" + eidasClientProperties.getIdpMetadataSigningCertificateKeyId() + "' in your "
                        + eidasClientProperties.getKeystore() + " keystore. We need it in order to verify IDP metadata's signature.");
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

    @ConditionalOnProperty(name = "eidas.client.hazelcast-enabled", havingValue = "false", matchIfMissing = true)
    @Bean(name = "requestSessionService")
    public RequestSessionService requestSessionService() {
        return new LocalRequestSessionServiceImpl(eidasClientProperties);
    }

    @Bean
    public AuthInitiationService authInitiationService(@Qualifier("requestSessionService") RequestSessionService requestSessionService,
                                                       @Qualifier("authnReqSigningCredential") Credential signingCredential, IDPMetadataResolver idpMetadataResolver,
                                                       ApplicationEventPublisher applicationEventPublisher) {
        return new AuthInitiationService(requestSessionService, signingCredential, eidasClientProperties, idpMetadataResolver, applicationEventPublisher);
    }

    @Bean
    public AuthResponseService authResponseService(RequestSessionService requestSessionService, IDPMetadataResolver idpMetadataResolver, Schema samlSchema,
                                                   @Qualifier("responseAssertionDecryptionCredential") Credential responseAssertionDecryptionCredential,
                                                   ApplicationEventPublisher applicationEventPublisher) {
        return new AuthResponseService(requestSessionService, eidasClientProperties, idpMetadataResolver, responseAssertionDecryptionCredential, applicationEventPublisher, samlSchema);
    }
}
