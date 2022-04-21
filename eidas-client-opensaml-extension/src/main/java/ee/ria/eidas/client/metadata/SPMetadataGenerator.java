package ee.ria.eidas.client.metadata;

import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.config.EidasCredentialsConfiguration.FailedCredentialEvent;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import ee.ria.eidas.client.util.SAMLSigner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.joda.time.DateTime;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.impl.XSAnyBuilder;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.ext.saml2alg.DigestMethod;
import org.opensaml.saml.ext.saml2alg.SigningMethod;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.metadata.*;
import org.opensaml.security.SecurityException;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.UsageType;
import org.opensaml.xmlsec.config.impl.DefaultSecurityConfigurationBootstrap;
import org.opensaml.xmlsec.keyinfo.KeyInfoGenerator;
import org.opensaml.xmlsec.keyinfo.NamedKeyInfoGeneratorManager;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class SPMetadataGenerator {

    private final EidasClientProperties eidasClientProperties;

    private final Credential metadataSigningCredential;

    private final Credential authnRequestSignCredential;

    private final Credential responseAssertionDecryptionCredential;

    private final ApplicationEventPublisher applicationEventPublisher;

    protected int defaultACSIndex = 0;

    public EntityDescriptor getMetadata() {
        try {
            EntityDescriptor entityDescriptor = buildEntityDescriptor();
            new SAMLSigner(eidasClientProperties.getMetadataSignatureAlgorithm(), metadataSigningCredential).sign(entityDescriptor);
            log.info("Successfully generated metadata. Metadata ID: {}", entityDescriptor.getID());
            if (log.isDebugEnabled()) {
                log.debug("Generated metadata: {}", OpenSAMLUtils.getXmlString(entityDescriptor));
            }
            return entityDescriptor;
        } catch (Exception e) {
            applicationEventPublisher.publishEvent(new FailedCredentialEvent(metadataSigningCredential));
            throw new EidasClientException("Error generating metadata", e);
        }
    }

    private EntityDescriptor buildEntityDescriptor() {
        EntityDescriptor descriptor = OpenSAMLUtils.buildSAMLObject(EntityDescriptor.class);
        descriptor.setEntityID(eidasClientProperties.getSpEntityId());
        descriptor.setValidUntil(DateTime.now().plusDays(eidasClientProperties.getMetadataValidityInDays()));
        descriptor.setID(generateEntityDescriptorId());
        descriptor.setExtensions(generateMetadataExtensions());
        descriptor.getRoleDescriptors().add(buildSPSSODescriptor());
        return descriptor;
    }

    private Extensions generateMetadataExtensions() {
        Extensions extensions = OpenSAMLUtils.buildSAMLObject(Extensions.class);
        extensions.getNamespaceManager().registerAttributeName(DigestMethod.TYPE_NAME);
        addUsedSigingMethodsToExtensions(extensions);

        return extensions;
    }

    private void addUsedSigingMethodsToExtensions(Extensions extensions) {
        Set<String> usedSigningMethods = new LinkedHashSet<String>();
        usedSigningMethods.add(eidasClientProperties.getMetadataSignatureAlgorithm());
        usedSigningMethods.add(eidasClientProperties.getRequestSignatureAlgorithm());
        usedSigningMethods.forEach(signingMethod -> {
            SigningMethod method = OpenSAMLUtils.buildSAMLObject(SigningMethod.class);
            method.setAlgorithm(signingMethod);
            extensions.getUnknownXMLObjects().add(method);
        });
    }

    private String generateEntityDescriptorId() {
        return "_".concat(RandomStringUtils.randomAlphanumeric(39)).toLowerCase();
    }

    private SPSSODescriptor buildSPSSODescriptor() {
        SPSSODescriptor spDescriptor = OpenSAMLUtils.buildSAMLObject(SPSSODescriptor.class);

        spDescriptor.setAuthnRequestsSigned(true);
        spDescriptor.setWantAssertionsSigned(true);

        spDescriptor.addSupportedProtocol(SAMLConstants.SAML20P_NS);
        spDescriptor.getNameIDFormats().addAll(buildNameIDFormat());

        int index = 0;
        spDescriptor.getAssertionConsumerServices().add(getAssertionConsumerService(SAMLConstants.SAML2_POST_BINDING_URI, index++, this.defaultACSIndex == index));

        spDescriptor.getKeyDescriptors().add(getKeyDescriptor(UsageType.SIGNING,
                generateKeyInfoForCredential(authnRequestSignCredential)));

        spDescriptor.getKeyDescriptors().add(getKeyDescriptor(UsageType.ENCRYPTION,
                generateKeyInfoForCredential(responseAssertionDecryptionCredential)));


        return spDescriptor;
    }

    private Collection<NameIDFormat> buildNameIDFormat() {
        Collection<NameIDFormat> formats = new LinkedList<>();
        NameIDFormat unspecNameID = OpenSAMLUtils.buildSAMLObject(NameIDFormat.class);
        unspecNameID.setFormat(NameIDType.UNSPECIFIED);
        formats.add(unspecNameID);
        return formats;
    }

    private AssertionConsumerService getAssertionConsumerService(String binding, int index,
                                                                 boolean isDefault) {
        AssertionConsumerService consumer = OpenSAMLUtils.buildSAMLObject(AssertionConsumerService.class);
        consumer.setLocation(eidasClientProperties.getCallbackUrl());
        consumer.setBinding(binding);
        if (isDefault) {
            consumer.setIsDefault(true);
        }
        consumer.setIndex(index);
        return consumer;
    }

    private KeyDescriptor getKeyDescriptor(UsageType type, KeyInfo key) {
        KeyDescriptor descriptor = OpenSAMLUtils.buildSAMLObject(KeyDescriptor.class);
        descriptor.setUse(type);
        descriptor.setKeyInfo(key);
        return descriptor;
    }

    private KeyInfo generateKeyInfoForCredential(Credential credential) {
        try {
            return getKeyInfoGenerator(credential).generate(credential);
        } catch (SecurityException e) {
            throw new EidasClientException("Unable to generate keyInfo from given credential", e);
        }
    }

    private KeyInfoGenerator getKeyInfoGenerator(Credential credential) {
        NamedKeyInfoGeneratorManager generatorManager = DefaultSecurityConfigurationBootstrap.buildBasicKeyInfoGeneratorManager();
        return generatorManager.getDefaultManager().getFactory(credential).newInstance();
    }
}
