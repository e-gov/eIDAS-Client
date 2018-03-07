package ee.ria.eidas.client.metadata;

import ee.ria.eidas.client.config.OpenSAMLConfiguration;
import ee.ria.eidas.client.exception.EidasClientException;
import net.shibboleth.ext.spring.resource.ResourceHelper;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import net.shibboleth.utilities.java.support.xml.ParserPool;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.RefreshableMetadataResolver;
import org.opensaml.saml.metadata.resolver.filter.impl.SignatureValidationFilter;
import org.opensaml.saml.metadata.resolver.impl.AbstractReloadingMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.DOMMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.HTTPMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.ResourceBackedMetadataResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.InputStream;

public class IDPMetadataResolver {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private Resource resource;
    private AbstractReloadingMetadataResolver idpMetadataProvider;
    private ExplicitKeySignatureTrustEngine metadataSignatureTrustEngine;
    private String entityId;

    public IDPMetadataResolver(Resource resource, ExplicitKeySignatureTrustEngine metadataSignatureTrustEngine) {
        this.resource = resource;
        this.metadataSignatureTrustEngine = metadataSignatureTrustEngine;
    }

    public AbstractReloadingMetadataResolver resolve() {
        if (idpMetadataProvider == null) {
            this.idpMetadataProvider = initNewResolver();
            this.entityId = getFirstEntityId();
            logger.debug("Using IDP with entityId: " + this.entityId);
        }
        return idpMetadataProvider;
    }

    public String getEntityId() {
        return entityId;
    }

    private AbstractReloadingMetadataResolver initNewResolver() {
        if (resource == null) {
            throw new EidasClientException("Idp metadata resource not set! Please check your configuration.");
        }

        try {
            ParserPool parserPool = OpenSAMLConfiguration.getParserPool();
            AbstractReloadingMetadataResolver idpMetadataResolver = new ResourceBackedMetadataResolver(ResourceHelper.of(resource));
            idpMetadataResolver.setParserPool(parserPool);
            idpMetadataResolver.setId(idpMetadataResolver.getClass().getCanonicalName());
            idpMetadataResolver.setMetadataFilter(new SignatureValidationFilter(metadataSignatureTrustEngine));
            idpMetadataResolver.setMinRefreshDelay(60000);
            idpMetadataResolver.initialize();
            return idpMetadataResolver;
        } catch (IOException e) {
            throw new EidasClientException("Error parsing IDP Metadata XML", e);
        } catch (ComponentInitializationException e) {
            throw new EidasClientException("Error initializing IDP Metadata provider", e);
        }
    }

    private String getFirstEntityId() {
        for (EntityDescriptor entityDescriptor : idpMetadataProvider) {
            return entityDescriptor.getEntityID();
        }
        throw new EidasClientException("No valid EntityDescriptors found!");
    }
}
