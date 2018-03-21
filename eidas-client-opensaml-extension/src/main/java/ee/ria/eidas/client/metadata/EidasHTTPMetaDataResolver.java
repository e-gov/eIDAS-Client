package ee.ria.eidas.client.metadata;

import ee.ria.eidas.client.exception.EidasClientException;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.apache.http.client.HttpClient;
import org.opensaml.saml.metadata.resolver.impl.HTTPMetadataResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;

public class EidasHTTPMetaDataResolver extends HTTPMetadataResolver {

    private boolean hostUrlValidationEnabled;

    public EidasHTTPMetaDataResolver(HttpClient client, String metadataURL, boolean hostUrlValidationEnabled) throws ResolverException {
        super(client, metadataURL);
        this.hostUrlValidationEnabled = hostUrlValidationEnabled;
    }

    @Override
    public synchronized void refresh() throws ResolverException {
        super.refresh();
        if (isInitialized()) {
            validateHostURL();
        }
    }

    public String getFirstEntityId() {
        for (EntityDescriptor entityDescriptor : this) {
            return entityDescriptor.getEntityID();
        }
        throw new EidasClientException("No valid EntityDescriptors found!");
    }

    public void validateHostURL() {
        if (hostUrlValidationEnabled) {
            String entityID = getFirstEntityId();
            if (!getMetadataURI().equals(entityID)) {
                throw new EidasClientException("entityID (" + entityID + ") does not match with requested URL (" + getMetadataURI() + ")");
            }
        }
    }

}
