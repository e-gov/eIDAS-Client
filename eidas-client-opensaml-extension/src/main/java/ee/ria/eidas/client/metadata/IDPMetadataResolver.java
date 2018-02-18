package ee.ria.eidas.client.metadata;

import ee.ria.eidas.client.config.OpenSAMLConfiguration;
import ee.ria.eidas.client.exception.EidasClientException;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.Criterion;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import net.shibboleth.utilities.java.support.xml.XMLParserException;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.criterion.EntityRoleCriterion;
import org.opensaml.saml.criterion.ProtocolCriterion;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.filter.impl.SignatureValidationFilter;
import org.opensaml.saml.metadata.resolver.impl.DOMMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.PredicateRoleDescriptorResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.security.impl.MetadataCredentialResolver;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.CredentialSupport;
import org.opensaml.security.credential.UsageType;
import org.opensaml.security.credential.impl.KeyStoreCredentialResolver;
import org.opensaml.security.credential.impl.StaticCredentialResolver;
import org.opensaml.security.criteria.UsageCriterion;
import org.opensaml.security.x509.X509Credential;
import org.opensaml.security.x509.X509Support;
import org.opensaml.xmlsec.config.DefaultSecurityConfigurationBootstrap;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.X509Certificate;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureTrustEngine;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.security.auth.login.Configuration;
import javax.xml.bind.ValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.Signature;
import java.util.HashMap;
import java.util.Map;

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
