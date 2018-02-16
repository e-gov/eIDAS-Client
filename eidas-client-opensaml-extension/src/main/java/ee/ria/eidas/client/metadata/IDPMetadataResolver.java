package ee.ria.eidas.client.metadata;

import ee.ria.eidas.client.config.OpenSAMLConfiguration;
import ee.ria.eidas.client.exception.EidasClientException;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.xml.XMLParserException;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.DOMMetadataResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.InputStream;

public class IDPMetadataResolver {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final InputStream idpMetadataInputStream;
    private String idpEntityId;
    private DOMMetadataResolver idpMetadataProvider;

    public IDPMetadataResolver(InputStream idpMetadataInputStream) {
        this.idpMetadataInputStream = idpMetadataInputStream;
    }

    public MetadataResolver resolve() {
        if (idpMetadataProvider != null) {
            return idpMetadataProvider;
        }
        try {

            if (this.idpMetadataInputStream == null) {
                throw new XMLParserException("IDP metadata resource is not defined");
            }
            try (InputStream is = idpMetadataInputStream) {
                final Document inCommonMDDoc = OpenSAMLConfiguration.getParserPool().parse(idpMetadataInputStream);
                final Element metadataRoot = inCommonMDDoc.getDocumentElement();
                idpMetadataProvider = new DOMMetadataResolver(metadataRoot);
                idpMetadataProvider.setParserPool(OpenSAMLConfiguration.getParserPool());
                idpMetadataProvider.setFailFastInitialization(true);
                idpMetadataProvider.setRequireValidMetadata(true);
                idpMetadataProvider.setId(idpMetadataProvider.getClass().getCanonicalName());
                idpMetadataProvider.initialize();
            }
            // If no idpEntityId declared, select first EntityDescriptor entityId as our IDP entityId
            if (this.idpEntityId == null) {

                for (EntityDescriptor entityDescriptor : idpMetadataProvider) {
                    if (IDPMetadataResolver.this.idpEntityId == null) {
                        IDPMetadataResolver.this.idpEntityId = entityDescriptor.getEntityID();
                    }
                }
            }
            if (this.idpEntityId == null) {
                throw new EidasClientException("No IDP entityId found");
            }
        } catch (ComponentInitializationException e) {
            throw new EidasClientException("Error initializing IDP Metadata Provider", e);
        } catch (IOException e) {
            throw new EidasClientException("Error getting IDP Metadata", e);
        } catch (XMLParserException e) {
            throw new EidasClientException("Error parsing IDP Metadata", e);
        }
        return idpMetadataProvider;
    }

}
