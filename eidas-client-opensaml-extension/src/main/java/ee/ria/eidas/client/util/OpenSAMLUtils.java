package ee.ria.eidas.client.util;

import ee.ria.eidas.client.exception.EidasClientException;
import net.shibboleth.utilities.java.support.security.RandomIdentifierGenerationStrategy;
import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.xmlsec.algorithm.AlgorithmDescriptor;
import org.opensaml.xmlsec.algorithm.AlgorithmSupport;
import org.opensaml.xmlsec.algorithm.DigestAlgorithm;
import org.opensaml.xmlsec.algorithm.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;

public class OpenSAMLUtils {

    private static RandomIdentifierGenerationStrategy secureRandomIdGenerator = new RandomIdentifierGenerationStrategy();

    public static <T> T buildSAMLObject(final Class<T> clazz) {
        T object;
        try {
            XMLObjectBuilderFactory builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory();
            QName defaultElementName = (QName) clazz.getDeclaredField("DEFAULT_ELEMENT_NAME").get(null);
            object = (T) builderFactory.getBuilder(defaultElementName).buildObject(defaultElementName);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new EidasClientException("Could not create SAML object");
        }

        return object;
    }

    public static String generateSecureRandomId() {
        return secureRandomIdGenerator.generateIdentifier();
    }

    public static String getXmlString(final XMLObject object) {
        try {
            Element entityDescriptorElement = XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(object).marshall(object);
            return SerializeSupport.nodeToString(entityDescriptorElement);
        } catch (MarshallingException e) {
            throw new EidasClientException("Error generating xml from: " + object);
        }
    }

    public static SignatureAlgorithm getSignatureAlgorithm(String signatureAlgorithmId) {
        AlgorithmDescriptor signatureAlgorithm = AlgorithmSupport.getGlobalAlgorithmRegistry().get(signatureAlgorithmId);
        Assert.notNull(signatureAlgorithm, "No signature algorithm support for: " + signatureAlgorithmId);
        Assert.isInstanceOf(SignatureAlgorithm.class, signatureAlgorithm, "This is not a valid XML signature algorithm! Please check your configuration!");
        return (SignatureAlgorithm) signatureAlgorithm;
    }

    public static DigestAlgorithm getRelatedDigestAlgorithm(String signatureAlgorithmId) {
        SignatureAlgorithm signatureAlgorithm = getSignatureAlgorithm(signatureAlgorithmId);
        DigestAlgorithm digestAlgorithm = AlgorithmSupport.getGlobalAlgorithmRegistry().getDigestAlgorithm(signatureAlgorithm.getDigest());
        Assert.notNull(digestAlgorithm, "No corresponding message digest algorithm support for signature algorithm: " + signatureAlgorithm.getURI());
        return digestAlgorithm;
    }
}
