package ee.ria.eidas.client.webapp.config;

import ee.ria.eidas.CustomSAML2ServiceProviderMetadataResolver;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.redirect.RedirectAction;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.client.SAML2ClientConfiguration;
import org.pac4j.saml.context.SAML2MessageContext;
import org.pac4j.saml.metadata.SAML2ServiceProviderMetadataResolver;
import org.pac4j.saml.redirect.SAML2RedirectActionBuilder;
import org.pac4j.saml.sso.impl.SAML2AuthnRequestBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.UrlResource;

import java.net.MalformedURLException;
import java.util.Arrays;

@Configuration
@EnableConfigurationProperties({
        EidasClientProperties.class
})
public class EidasClientConfig {

    @Autowired
    private EidasClientProperties eidasClientProperties;

    @Bean
    public SAML2ClientConfiguration saml2ClientConfiguration() throws MalformedURLException {
        final SAML2ClientConfiguration cfg = new SAML2ClientConfiguration(
                new ClassPathResource(eidasClientProperties.getKeystore()),
                eidasClientProperties.getKeystorePass(),
                eidasClientProperties.getKeystorePass(),
                new UrlResource(eidasClientProperties.getIdpMetadataUrl()));
        cfg.setDestinationBindingType(SAMLConstants.SAML2_POST_BINDING_URI);
        cfg.setMaximumAuthenticationLifetime(eidasClientProperties.getMaximumAuthenticationLifetime());
        cfg.setServiceProviderEntityId(eidasClientProperties.getSpEntityId());
        cfg.setForceAuth(true);
        cfg.setForceSignRedirectBindingAuthnRequest(true);
        cfg.setWantsAssertionsSigned(true);
        cfg.setNameIdPolicyFormat(eidasClientProperties.getNameIdPolicyFormat());
        cfg.setSignatureAlgorithms(Arrays.asList("http://www.w3.org/2001/04/xmldsig-more#sha512"));
        cfg.setBlackListedSignatureSigningAlgorithms(Arrays.asList("http://www.w3.org/2000/09/xmldsig#rsa-sha1", "http://www.w3.org/2000/09/xmldsig#dsa-sha1"));
        cfg.setSignatureReferenceDigestMethods(Arrays.asList("http://www.w3.org/2001/04/xmldsig-more#sha512"));
        return cfg;
    }

    @Bean
    public SAML2Client saml2Client(SAML2ClientConfiguration saml2ClientConfiguration) {
        final SAML2Client saml2Client = new EidasSAML2Client(saml2ClientConfiguration);
        saml2Client.setRedirectActionBuilder(new EidasRedirectActionBuilder(saml2Client));
        saml2Client.setCallbackUrl(eidasClientProperties.getCallbackUrl());
        saml2Client.init(null);
        return saml2Client;
    }

    class EidasRedirectActionBuilder extends SAML2RedirectActionBuilder {

        public EidasRedirectActionBuilder(SAML2Client client) {
            super(client);
            this.saml2ObjectBuilder = new EidasSAML2AuthnRequestBuilder(client.getConfiguration());
        }

        @Override
        public RedirectAction redirect(WebContext wc) throws HttpAction {
            System.out.println("Create custom RedirectActionBuilder, to modify AuthnRequest");
            // TODO add Eidas specific extensisons here! (Country code, RelayState)
            return super.redirect(wc);
        }
    }

    class EidasSAML2AuthnRequestBuilder extends SAML2AuthnRequestBuilder {

        public EidasSAML2AuthnRequestBuilder(SAML2ClientConfiguration cfg) {
            super(cfg);
        }

        @Override
        public AuthnRequest build(SAML2MessageContext context) {
            AuthnRequest authnRequest = super.build(context);
            // TODO add Eidas specific extensisons and SPType here!
            return authnRequest;
        }
    }

    class EidasSAML2Client extends SAML2Client {

        public EidasSAML2Client(SAML2ClientConfiguration saml2ClientConfiguration) {
            super(saml2ClientConfiguration);
        }

        @Override
        protected MetadataResolver initServiceProviderMetadataResolver(WebContext context) {
            this.spMetadataResolver = new CustomSAML2ServiceProviderMetadataResolver(this.configuration,
                    computeFinalCallbackUrl(context),
                    this.credentialProvider);
            return this.spMetadataResolver.resolve();
        }
    }
}
