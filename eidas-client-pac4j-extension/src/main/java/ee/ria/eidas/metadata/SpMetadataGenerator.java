package ee.ria.eidas.metadata;

import ee.ria.eidas.config.OpenSamlConfiguration;
import ee.ria.eidas.config.EidasClientProperties;
import ee.ria.eidas.util.OpenSAMLUtils;
import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import org.apache.commons.lang.RandomStringUtils;
import org.joda.time.DateTime;
import org.opensaml.core.xml.io.MarshallerFactory;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.ext.saml2alg.DigestMethod;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.DOMMetadataResolver;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.metadata.*;
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

public class SpMetadataGenerator {

    protected final static Logger logger = LoggerFactory.getLogger(SpMetadataGenerator.class);

    protected final MarshallerFactory marshallerFactory = OpenSamlConfiguration.getMarshallerFactory();

    protected int defaultACSIndex = 0;

    private final EidasClientProperties eidasClientProperties;
    private final Credential metadataSigningCredential;
    private final Credential authnRequestSignCredential;
    private final Credential responseAssertionDecryptionCredential;

    public SpMetadataGenerator(EidasClientProperties eidasClientProperties, Credential metadataSigningCredential, Credential authnRequestSignCredential, Credential responseAssertionDecryptionCredential) {
        this.eidasClientProperties = eidasClientProperties;
        this.metadataSigningCredential = metadataSigningCredential;
        this.authnRequestSignCredential = authnRequestSignCredential;
        this.responseAssertionDecryptionCredential = responseAssertionDecryptionCredential;
    }

    public final String getMetadata() {
        try {
            Signature signature = (Signature) OpenSamlConfiguration.getBuilderFactory()
                    .getBuilder(Signature.DEFAULT_ELEMENT_NAME)
                    .buildObject(Signature.DEFAULT_ELEMENT_NAME);
            signature.setSigningCredential(metadataSigningCredential);
            signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA512);
            signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

            X509KeyInfoGeneratorFactory x509KeyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
            x509KeyInfoGeneratorFactory.setEmitEntityCertificate(true);
            KeyInfo keyInfo = x509KeyInfoGeneratorFactory.newInstance().generate(metadataSigningCredential);
            signature.setKeyInfo(keyInfo);


            final EntityDescriptor md = buildEntityDescriptor();
            md.setSignature(signature);
            final Element entityDescriptorElement = this.marshallerFactory.getMarshaller(md).marshall(md);
            Signer.signObject(signature);
            return SerializeSupport.nodeToString(entityDescriptorElement);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create metadata", e);
        }
    }

    public final EntityDescriptor buildEntityDescriptor() {
        EntityDescriptor descriptor = OpenSAMLUtils.buildSAMLObject(EntityDescriptor.class);
        descriptor.setEntityID(eidasClientProperties.getSpEntityId());
        descriptor.setValidUntil(DateTime.now().plusYears(20));
        descriptor.setID(generateEntityDescriptorId());
        descriptor.setExtensions(generateMetadataExtensions());
        descriptor.getRoleDescriptors().add(buildSPSSODescriptor());
        return descriptor;
    }

    protected final Extensions generateMetadataExtensions() {
        final Extensions extensions = OpenSAMLUtils.buildSAMLObject(Extensions.class);
        extensions.getNamespaceManager().registerAttributeName(DigestMethod.TYPE_NAME);

        DigestMethod method = OpenSAMLUtils.buildSAMLObject(DigestMethod.class);
        method.setAlgorithm("http://www.w3.org/2001/04/xmlenc#sha512");
        extensions.getUnknownXMLObjects().add(method);

        return extensions;
    }

    protected final String generateEntityDescriptorId() {
        try {
            return "_".concat(RandomStringUtils.randomAlphanumeric(39)).toLowerCase();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected final SPSSODescriptor buildSPSSODescriptor() {
        final SPSSODescriptor spDescriptor = OpenSAMLUtils.buildSAMLObject(SPSSODescriptor.class);

        spDescriptor.setAuthnRequestsSigned(true);
        spDescriptor.setWantAssertionsSigned(true);

        spDescriptor.addSupportedProtocol(SAMLConstants.SAML20P_NS);

        spDescriptor.getNameIDFormats().addAll(buildNameIDFormat());

        int index = 0;
        spDescriptor.getAssertionConsumerServices().add(getAssertionConsumerService(SAMLConstants.SAML2_POST_BINDING_URI, index++, this.defaultACSIndex == index));


            // TODO must be configurable
            spDescriptor.getKeyDescriptors().add(getKeyDescriptor(UsageType.SIGNING,
                    generateKeyInfoForCredential(authnRequestSignCredential)));

            KeyDescriptor encryptionKeyDescriptor = getKeyDescriptor(UsageType.ENCRYPTION,
            generateKeyInfoForCredential(responseAssertionDecryptionCredential));

            EncryptionMethod encryptionMethod = OpenSAMLUtils.buildSAMLObject(EncryptionMethod.class);
            encryptionMethod.setAlgorithm("http://www.w3.org/2009/xmlenc11#aes192-gcm");
            encryptionKeyDescriptor.getEncryptionMethods().add(encryptionMethod);

            encryptionMethod = OpenSAMLUtils.buildSAMLObject(EncryptionMethod.class);
            encryptionMethod.setAlgorithm("http://www.w3.org/2001/04/xmlenc#rsa-oaep-mgf1p");
            encryptionKeyDescriptor.getEncryptionMethods().add(encryptionMethod);

            encryptionMethod = OpenSAMLUtils.buildSAMLObject(EncryptionMethod.class);
            encryptionMethod.setAlgorithm("http://www.w3.org/2009/xmlenc11#aes256-gcm");
            encryptionKeyDescriptor.getEncryptionMethods().add(encryptionMethod);

            encryptionMethod = OpenSAMLUtils.buildSAMLObject(EncryptionMethod.class);
            encryptionMethod.setAlgorithm("http://www.w3.org/2009/xmlenc11#aes128-gcm");
            encryptionKeyDescriptor.getEncryptionMethods().add(encryptionMethod);


            spDescriptor.getKeyDescriptors().add(encryptionKeyDescriptor);


        return spDescriptor;

    }

    protected final Collection<NameIDFormat> buildNameIDFormat() {
        final Collection<NameIDFormat> formats = new LinkedList<NameIDFormat>();
        final NameIDFormat transientNameID = OpenSAMLUtils.buildSAMLObject(NameIDFormat.class);
        transientNameID.setFormat(NameIDType.TRANSIENT);
        formats.add(transientNameID);
        final NameIDFormat persistentNameID = OpenSAMLUtils.buildSAMLObject(NameIDFormat.class);
        persistentNameID.setFormat(NameIDType.PERSISTENT);
        formats.add(persistentNameID);
        final NameIDFormat unspecNameID = OpenSAMLUtils.buildSAMLObject(NameIDFormat.class);
        unspecNameID.setFormat(NameIDType.UNSPECIFIED);
        formats.add(unspecNameID);
        return formats;
    }

    protected final AssertionConsumerService getAssertionConsumerService(final String binding, final int index,
                                                                         final boolean isDefault) {
        final AssertionConsumerService consumer = OpenSAMLUtils.buildSAMLObject(AssertionConsumerService.class);
        consumer.setLocation(eidasClientProperties.getCallbackUrl());
        consumer.setBinding(binding);
        if (isDefault) {
            consumer.setIsDefault(true);
        }
        consumer.setIndex(index);
        return consumer;
    }

    protected final KeyDescriptor getKeyDescriptor(final UsageType type, final KeyInfo key) {
        final KeyDescriptor descriptor = OpenSAMLUtils.buildSAMLObject(KeyDescriptor.class);
        descriptor.setUse(type);
        descriptor.setKeyInfo(key);
        return descriptor;
    }

    protected final KeyInfo generateKeyInfoForCredential(final Credential credential) {
        try {
            return getKeyInfoGenerator(credential).generate(credential);
        } catch (final org.opensaml.security.SecurityException e) {
            throw new RuntimeException("Unable to generate keyInfo from given credential", e);
        }
    }

    public final KeyInfoGenerator getKeyInfoGenerator(Credential credential) {
        final NamedKeyInfoGeneratorManager mgmr = DefaultSecurityConfigurationBootstrap.buildBasicKeyInfoGeneratorManager();
        return mgmr.getDefaultManager().getFactory(credential).newInstance();
    }
}
