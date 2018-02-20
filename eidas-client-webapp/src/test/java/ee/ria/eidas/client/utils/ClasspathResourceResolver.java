package ee.ria.eidas.client.utils;

import com.sun.org.apache.xerces.internal.dom.DOMInputImpl;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import java.io.File;
import java.io.InputStream;

public class ClasspathResourceResolver implements LSResourceResolver {

    public static final String SCHEMA_DIR_ON_CLASSPATH = "schema" + File.separator;

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
        InputStream resource = ClassLoader.getSystemResourceAsStream(SCHEMA_DIR_ON_CLASSPATH + systemId);
        return new DOMInputImpl(publicId, systemId, baseURI, resource, null);
    }
}