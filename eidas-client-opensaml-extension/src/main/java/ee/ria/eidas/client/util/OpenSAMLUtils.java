package ee.ria.eidas.client.util;

import ee.ria.eidas.client.exception.EidasClientException;
import net.shibboleth.utilities.java.support.security.RandomIdentifierGenerationStrategy;
import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.MarshallingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;

public class OpenSAMLUtils {
    private static Logger LOGGER = LoggerFactory.getLogger(OpenSAMLUtils.class);

    private static RandomIdentifierGenerationStrategy secureRandomIdGenerator = new RandomIdentifierGenerationStrategy();

    public static <T> T buildSAMLObject(final Class<T> clazz) {
        T object;
        try {
            XMLObjectBuilderFactory builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory();
            QName defaultElementName = (QName)clazz.getDeclaredField("DEFAULT_ELEMENT_NAME").get(null);
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
}
