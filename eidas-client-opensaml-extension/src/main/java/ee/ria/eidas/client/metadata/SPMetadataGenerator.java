package ee.ria.eidas.client.metadata;

import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import org.apache.commons.lang.RandomStringUtils;
import org.joda.time.DateTime;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.ext.saml2alg.DigestMethod;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.metadata.*;
import org.opensaml.security.SecurityException;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.UsageType;
import org.opensaml.xmlsec.config.DefaultSecurityConfigurationBootstrap;
import org.opensaml.xmlsec.keyinfo.KeyInfoGenerator;
import org.opensaml.xmlsec.keyinfo.NamedKeyInfoGeneratorManager;
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;

public class SPMetadataGenerator {

    protected static Logger LOGGER = LoggerFactory.getLogger(SPMetadataGenerator.class);

    protected int defaultACSIndex = 0;

    private EidasClientProperties eidasClientProperties;
    private Credential metadataSigningCredential;
    private Credential authnRequestSignCredential;
    private Credential responseAssertionDecryptionCredential;

    public SPMetadataGenerator(EidasClientProperties eidasClientProperties, Credential metadataSigningCredential, Credential authnRequestSignCredential, Credential responseAssertionDecryptionCredential) {
        this.eidasClientProperties = eidasClientProperties;
        this.metadataSigningCredential = metadataSigningCredential;
        this.authnRequestSignCredential = authnRequestSignCredential;
        this.responseAssertionDecryptionCredential = responseAssertionDecryptionCredential;
    }

    public EntityDescriptor getMetadata() {
        try {
            Signature signature = (Signature) XMLObjectProviderRegistrySupport.getBuilderFactory()
                    .getBuilder(Signature.DEFAULT_ELEMENT_NAME)
                    .buildObject(Signature.DEFAULT_ELEMENT_NAME);
            signature.setSigningCredential(metadataSigningCredential);
            signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA512);
            signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

            X509KeyInfoGeneratorFactory x509KeyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
            x509KeyInfoGeneratorFactory.setEmitEntityCertificate(true);
            KeyInfo keyInfo = x509KeyInfoGeneratorFactory.newInstance().generate(metadataSigningCredential);
            signature.setKeyInfo(keyInfo);

            EntityDescriptor entityDescriptor = buildEntityDescriptor();

            entityDescriptor.setSignature(signature);
            XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(entityDescriptor).marshall(entityDescriptor);
            Signer.signObject(signature);
            return entityDescriptor;
        } catch (Exception e) {
            throw new EidasClientException("Error generating metadata", e);
        }
    }

    private EntityDescriptor buildEntityDescriptor() {
        EntityDescriptor descriptor = OpenSAMLUtils.buildSAMLObject(EntityDescriptor.class);
        descriptor.setEntityID(eidasClientProperties.getSpEntityId());
        descriptor.setValidUntil(DateTime.now().plusYears(20));
        descriptor.setID(generateEntityDescriptorId());
        descriptor.setExtensions(generateMetadataExtensions());
        descriptor.getRoleDescriptors().add(buildSPSSODescriptor());
        return descriptor;
    }

    private Extensions generateMetadataExtensions() {
        Extensions extensions = OpenSAMLUtils.buildSAMLObject(Extensions.class);
        extensions.getNamespaceManager().registerAttributeName(DigestMethod.TYPE_NAME);

        DigestMethod method = OpenSAMLUtils.buildSAMLObject(DigestMethod.class);
        method.setAlgorithm("http://www.w3.org/2001/04/xmlenc#sha512");
        extensions.getUnknownXMLObjects().add(method);

        return extensions;
    }

    private String generateEntityDescriptorId() {
        try {
            return "_".concat(RandomStringUtils.randomAlphanumeric(39)).toLowerCase();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        Collection<NameIDFormat> formats = new LinkedList<NameIDFormat>();
        NameIDFormat transientNameID = OpenSAMLUtils.buildSAMLObject(NameIDFormat.class);
        transientNameID.setFormat(NameIDType.TRANSIENT);
        formats.add(transientNameID);
        NameIDFormat persistentNameID = OpenSAMLUtils.buildSAMLObject(NameIDFormat.class);
        persistentNameID.setFormat(NameIDType.PERSISTENT);
        formats.add(persistentNameID);
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
