package ee.ria.eidas.client.metadata;

import ee.ria.eidas.client.authnrequest.SPType;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.config.OpenSAMLConfiguration;
import ee.ria.eidas.client.exception.EidasClientException;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.shared.component.ComponentInitializationException;
import net.shibboleth.shared.resolver.CriteriaSet;
import net.shibboleth.shared.resolver.ResolverException;
import net.shibboleth.shared.spring.resource.ResourceHelper;
import net.shibboleth.shared.xml.ParserPool;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.schema.impl.XSAnyImpl;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.criterion.EntityRoleCriterion;
import org.opensaml.saml.criterion.ProtocolCriterion;
import org.opensaml.saml.metadata.resolver.filter.impl.SignatureValidationFilter;
import org.opensaml.saml.metadata.resolver.impl.AbstractReloadingMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.HTTPMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.ResourceBackedMetadataResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.credential.CredentialSupport;
import org.opensaml.security.credential.UsageType;
import org.opensaml.security.credential.impl.StaticCredentialResolver;
import org.opensaml.security.criteria.UsageCriterion;
import org.opensaml.security.x509.X509Credential;
import org.opensaml.xmlsec.config.impl.DefaultSecurityConfigurationBootstrap;
import org.opensaml.xmlsec.signature.impl.X509CertificateImpl;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Slf4j
public class IDPMetadataResolver {

    private String url;

    private AbstractReloadingMetadataResolver idpMetadataProvider;

    private ExplicitKeySignatureTrustEngine metadataSignatureTrustEngine;

    private ParserPool parserPool;

    @Autowired
    private EidasClientProperties eidasClientProperties;

    public IDPMetadataResolver(String url, ExplicitKeySignatureTrustEngine metadataSignatureTrustEngine) {
        this.url = url;
        this.metadataSignatureTrustEngine = metadataSignatureTrustEngine;
        this.parserPool = OpenSAMLConfiguration.getParserPool();
    }

    public AbstractReloadingMetadataResolver resolve() {
        if (idpMetadataProvider == null) {
            this.idpMetadataProvider = initNewResolver();
            if (!isEntityIdPresent(url)) {
                throw new EidasClientException("No valid EntityDescriptor with entityID = '" + url + "' was found!");
            }
        }
        return idpMetadataProvider;
    }

    private AbstractReloadingMetadataResolver initNewResolver() {
        if (url == null) {
            throw new EidasClientException("Idp metadata resource not set! Please check your configuration.");
        }

        try {
            final CriteriaSet criteriaSet = new CriteriaSet();
            criteriaSet.add(new UsageCriterion(UsageType.SIGNING));
            criteriaSet.add(new EntityRoleCriterion(IDPSSODescriptor.DEFAULT_ELEMENT_NAME));
            criteriaSet.add(new ProtocolCriterion(SAMLConstants.SAML20P_NS));
            criteriaSet.add(new EntityIdCriterion(url));

            SignatureValidationFilter signatureValidationFilter = new SignatureValidationFilter(metadataSignatureTrustEngine);
            signatureValidationFilter.setDefaultCriteria(criteriaSet);
            signatureValidationFilter.initialize();

            AbstractReloadingMetadataResolver idpMetadataResolver = getMetadataResolver(url);
            idpMetadataResolver.setParserPool(parserPool);
            idpMetadataResolver.setId(idpMetadataResolver.getClass().getCanonicalName());
            idpMetadataResolver.setMetadataFilter(signatureValidationFilter);
            idpMetadataResolver.setMinRefreshDelay(Duration.ofMillis(60000));
            idpMetadataResolver.initialize();
            return idpMetadataResolver;
        } catch (ComponentInitializationException e) {
            throw new EidasClientException("Error initializing IDP Metadata provider.", e);
        }
    }

    private boolean isEntityIdPresent(String idpMetadataUrl) {
        Iterable<EntityDescriptor> iterable = () -> idpMetadataProvider.iterator();
        return StreamSupport.stream(iterable.spliterator(), false).anyMatch(x -> Objects.equals(x.getEntityID(), idpMetadataUrl));
    }

    private AbstractReloadingMetadataResolver getMetadataResolver(String url) {
        try {
            if (url.startsWith(ResourceLoader.CLASSPATH_URL_PREFIX)) {
                ClassPathResource resource = new ClassPathResource(url.substring(ResourceLoader.CLASSPATH_URL_PREFIX.length()));
                return new ResourceBackedMetadataResolver(ResourceHelper.of(resource));
            } else {
                CloseableHttpClient httpclient = HttpClients.createDefault();
                return new HTTPMetadataResolver(httpclient, url);
            }
        } catch (IOException | ResolverException e) {
            throw new EidasClientException("Error resolving IDP Metadata", e);
        }
    }

    public SingleSignOnService getSingeSignOnService() {
        try {
            AbstractReloadingMetadataResolver metadataResolver = this.resolve();
            CriteriaSet criteriaSet = new CriteriaSet(new EntityIdCriterion(url));
            EntityDescriptor entityDescriptor = metadataResolver.resolveSingle(criteriaSet);
            if (entityDescriptor == null) {
                throw new EidasClientException("Could not find a valid EntityDescriptor in your IDP metadata! ");
            }

            for (SingleSignOnService ssoService : entityDescriptor.getIDPSSODescriptor(SAMLConstants.SAML20P_NS).getSingleSignOnServices()) {
                if (ssoService.getBinding().equals(SAMLConstants.SAML2_POST_BINDING_URI)) {
                    return ssoService;
                }
            }
        } catch (final ResolverException e) {
            throw new EidasClientException("Error initializing IDP metadata", e);
        }
        throw new EidasClientException("Could not find a valid SAML2 POST BINDING from IDP metadata!");
    }

    public Map<SPType, List<String>> getSupportedCountries() {
        try {
            AbstractReloadingMetadataResolver metadataResolver = this.resolve();
            CriteriaSet criteriaSet = new CriteriaSet(new EntityIdCriterion(url));
            EntityDescriptor entityDescriptor = metadataResolver.resolveSingle(criteriaSet);

            Map<SPType, List<String >> supportedCountries = new HashMap<>();

            List<String> publicSectorSupportedCountries = getPublicSectorSupportedCountries(entityDescriptor);
            if (publicSectorSupportedCountries.isEmpty()) {
                log.error("Unable to get supported countries from metadata. Using supported countries from configuration.");
                supportedCountries.put(SPType.PUBLIC, eidasClientProperties.getAvailableCountriesPublicFallback());
            } else {
                supportedCountries.put(SPType.PUBLIC, publicSectorSupportedCountries);
            }
            supportedCountries.put(SPType.PRIVATE, eidasClientProperties.getAvailableCountriesPrivate());
            return supportedCountries;
        } catch (final ResolverException e) {
            throw new EidasClientException("Error initializing IDP metadata", e);
        }
    }

    protected List<String> getPublicSectorSupportedCountries(EntityDescriptor entityDescriptor) {
        if (entityDescriptor == null) {
            log.error("Could not find a valid EntityDescriptor in your IDP metadata!");
            return new ArrayList<>();
        }

        List<String> supportedCountries = new ArrayList<>();

        if (entityDescriptor.getExtensions().hasChildren()) {
            for (XMLObject mainXmlObject : entityDescriptor.getExtensions().getOrderedChildren()) {
                if (mainXmlObject.getElementQName().getLocalPart().equals("SupportedMemberStates") && mainXmlObject.hasChildren()) {
                    for (XMLObject xmlObject : mainXmlObject.getOrderedChildren()) {
                        if (xmlObject instanceof XSAnyImpl &&
                                xmlObject.getElementQName().getLocalPart().equals("MemberState") &&
                                ((XSAnyImpl) xmlObject).getTextContent() != null) {
                            supportedCountries.add(((XSAnyImpl) xmlObject).getTextContent());
                        }
                    }
                }
            }
        }

        return supportedCountries;
    }

    public ExplicitKeySignatureTrustEngine responseSignatureTrustEngine() {
        X509Certificate cert = getResponseSigningCertificate(this);
        X509Credential switchCred = CredentialSupport.getSimpleCredential(cert, null);
        StaticCredentialResolver switchCredResolver = new StaticCredentialResolver(switchCred);
        return new ExplicitKeySignatureTrustEngine(switchCredResolver, DefaultSecurityConfigurationBootstrap.buildBasicInlineKeyInfoCredentialResolver());
    }

    private X509Certificate getResponseSigningCertificate(IDPMetadataResolver idpMetadataResolver) {
        try {
            List<KeyDescriptor> idpSsoKeyDescriptors = idpMetadataResolver.resolve().iterator().next().getIDPSSODescriptor(SAMLConstants.SAML20P_NS).getKeyDescriptors();
            Optional<KeyDescriptor> matchingDescriptor = idpSsoKeyDescriptors.stream().
                    filter(d -> d.getUse() == UsageType.SIGNING).findFirst();
            KeyDescriptor signingDescriptor = matchingDescriptor.orElse(null);
            if (signingDescriptor == null) {
                throw new EidasClientException("Could not find signing descriptor from IDP metadata");
            }
            X509CertificateImpl certificate = (X509CertificateImpl) signingDescriptor.getKeyInfo().getX509Datas().get(0).getX509Certificates().get(0);
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream certInputStream = new ByteArrayInputStream(Base64.getDecoder().decode(certificate.getValue().replaceAll("\n", "")));
            return (X509Certificate) certFactory.generateCertificate(certInputStream);
        } catch (CertificateException e) {
            throw new EidasClientException("Error initializing. Cannot get IDP metadata trusted certificate", e);
        }
    }
}
