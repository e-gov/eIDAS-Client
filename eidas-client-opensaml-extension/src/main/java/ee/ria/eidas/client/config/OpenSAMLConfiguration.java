package ee.ria.eidas.client.config;

import ee.ria.eidas.client.exception.EidasClientException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.shared.component.ComponentInitializationException;
import net.shibboleth.shared.xml.ParserPool;
import net.shibboleth.shared.xml.impl.BasicParserPool;
import se.swedenconnect.opensaml.OpenSAMLInitializer;
import se.swedenconnect.opensaml.OpenSAMLSecurityDefaultsConfig;
import se.swedenconnect.opensaml.OpenSAMLSecurityExtensionConfig;
import se.swedenconnect.opensaml.xmlsec.config.SAML2IntSecurityConfiguration;

import java.util.HashMap;
import java.util.Map;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

@Slf4j
public class OpenSAMLConfiguration {

    @Getter
    private static final ParserPool parserPool;

    private OpenSAMLConfiguration() {
    }

    static {
        try {
            OpenSAMLInitializer bootstrapper = OpenSAMLInitializer.getInstance();
            parserPool = setupParserPool();
            bootstrapper.setParserPool(parserPool);
            bootstrapper.initialize(
                    new OpenSAMLSecurityDefaultsConfig(new SAML2IntSecurityConfiguration()),
                    new OpenSAMLSecurityExtensionConfig());
        } catch (Exception e) {
            throw new EidasClientException("Error initializing OpenSAML", e);
        }
    }

    public static ParserPool setupParserPool() {
        Map<String, Boolean> features = new HashMap<>();
        features.put("http://apache.org/xml/features/disallow-doctype-decl", TRUE);
        features.put("http://apache.org/xml/features/validation/schema/normalized-value", FALSE);
        features.put("http://javax.xml.XMLConstants/feature/secure-processing", TRUE);
        features.put("http://xml.org/sax/features/external-general-entities", FALSE);
        features.put("http://xml.org/sax/features/external-parameter-entities", FALSE);

        BasicParserPool parserPool = new BasicParserPool();
        parserPool.setMaxPoolSize(100);
        parserPool.setCoalescing(true);
        parserPool.setIgnoreComments(true);
        parserPool.setNamespaceAware(true);
        parserPool.setExpandEntityReferences(false);
        parserPool.setXincludeAware(false);
        parserPool.setIgnoreElementContentWhitespace(true);
        parserPool.setBuilderAttributes(new HashMap<>());
        parserPool.setBuilderFeatures(features);
        try {
            parserPool.initialize();
        } catch (final ComponentInitializationException e) {
            throw new EidasClientException("Error initializing parserPool", e);
        }
        return parserPool;
    }
}
