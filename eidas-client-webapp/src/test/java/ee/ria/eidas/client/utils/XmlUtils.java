package ee.ria.eidas.client.utils;

import net.shibboleth.utilities.java.support.xml.QNameSupport;
import net.shibboleth.utilities.java.support.xml.XMLParserException;
import org.junit.Assert;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class XmlUtils {

    public static <T extends org.opensaml.core.xml.XMLObject> T unmarshallElement(String xml) {
        try {
            final Document doc = parseXMLDocument(xml);
            final Element element = doc.getDocumentElement();
            final Unmarshaller unmarshaller = getUnmarshaller(element);
            final T object = (T) unmarshaller.unmarshall(element);
            Assert.assertNotNull(object);
            return object;
        } catch (final XMLParserException e) {
            throw new RuntimeException("Unable to parse element file " + xml);
        } catch (final UnmarshallingException e) {
            throw new RuntimeException("Unmarshalling failed when parsing element file " + xml + ": " + e);
        }
    }

    private static Document parseXMLDocument(String xml) throws XMLParserException {
        InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        Document doc = XMLObjectProviderRegistrySupport.getParserPool().parse(is);
        return doc;
    }

    private static Unmarshaller getUnmarshaller(Element element) {
        Unmarshaller unmarshaller = XMLObjectProviderRegistrySupport.getUnmarshallerFactory().getUnmarshaller(element);
        if (unmarshaller == null) {
            Assert.fail("no unmarshaller registered for " + QNameSupport.getNodeQName(element));
        }
        return unmarshaller;
    }
}
