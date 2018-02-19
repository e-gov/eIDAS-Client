package ee.ria.eidas.client.metadata;

import ee.ria.eidas.client.config.OpenSAMLConfiguration;
import ee.ria.eidas.client.exception.EidasClientException;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.filter.impl.SignatureValidationFilter;
import org.opensaml.saml.metadata.resolver.impl.DOMMetadataResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.InputStream;

public class IDPMetadataResolver {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final InputStream idpMetadataInputStream;
    private DOMMetadataResolver idpMetadataProvider;
    private ExplicitKeySignatureTrustEngine metadataSignatureTrustEngine;
    private String entityId;

    public IDPMetadataResolver(InputStream idpMetadataInputStream, ExplicitKeySignatureTrustEngine metadataSignatureTrustEngine) {
        this.idpMetadataInputStream = idpMetadataInputStream;
        this.metadataSignatureTrustEngine = metadataSignatureTrustEngine;
    }

    public MetadataResolver resolve() {
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

    private DOMMetadataResolver initNewResolver() {
        if (this.idpMetadataInputStream == null) {
            throw new EidasClientException("Idp metadata was not found. Please check your configuration.");
        }

        try {
            try (InputStream is = idpMetadataInputStream) {
                final Document inCommonMDDoc = OpenSAMLConfiguration.getParserPool().parse(idpMetadataInputStream);
                final Element metadataRoot = inCommonMDDoc.getDocumentElement();
                idpMetadataProvider = new DOMMetadataResolver(metadataRoot);
                idpMetadataProvider.setParserPool(OpenSAMLConfiguration.getParserPool());
                idpMetadataProvider.setFailFastInitialization(true);
                idpMetadataProvider.setRequireValidMetadata(true);
                idpMetadataProvider.setId(idpMetadataProvider.getClass().getCanonicalName());
                idpMetadataProvider.setMetadataFilter(new SignatureValidationFilter(metadataSignatureTrustEngine));
                idpMetadataProvider.initialize();
            }
        } catch (ComponentInitializationException e) {
            throw new EidasClientException("Error initializing IDP Metadata Provider", e);
        } catch (IOException e) {
            throw new EidasClientException("Error getting IDP Metadata", e);
        } catch (Exception e) {
            throw new EidasClientException("Error parsing IDP Metadata XML", e);
        }
        return idpMetadataProvider;
    }

    private String getFirstEntityId() {
        for (EntityDescriptor entityDescriptor : idpMetadataProvider) {
            return entityDescriptor.getEntityID();
        }

        throw new IllegalStateException("No valid EntityDescriptors found!");
    }
}
