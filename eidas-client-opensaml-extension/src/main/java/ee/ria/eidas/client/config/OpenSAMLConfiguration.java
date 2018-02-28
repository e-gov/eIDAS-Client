package ee.ria.eidas.client.config;

import ee.ria.eidas.client.exception.EidasClientException;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;
import net.shibboleth.utilities.java.support.xml.ParserPool;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.xmlsec.algorithm.AlgorithmRegistry;
import org.opensaml.xmlsec.algorithm.AlgorithmSupport;
import org.opensaml.xmlsec.algorithm.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.security.Provider;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

public class OpenSAMLConfiguration {
    protected static final Logger LOGGER = LoggerFactory.getLogger(OpenSAMLConfiguration.class);

    private static BasicParserPool parserPool;

    private OpenSAMLConfiguration() {}

    static {
        LOGGER.info("Bootstrapping OpenSAML configuration");
        Security.addProvider(new BouncyCastleProvider());
        bootstrap();
    }

    public static ParserPool getParserPool () {
        return parserPool;
    }

    private static void bootstrap() {
        parserPool = new BasicParserPool();
        parserPool.setMaxPoolSize(100);
        parserPool.setCoalescing(true);
        parserPool.setIgnoreComments(true);
        parserPool.setNamespaceAware(true);
        parserPool.setExpandEntityReferences(false);
        parserPool.setXincludeAware(false);
        parserPool.setIgnoreElementContentWhitespace(true);

        Map<String, Object> builderAttributes = new HashMap<>();
        parserPool.setBuilderAttributes(builderAttributes);

        Map<String, Boolean> features = new HashMap<>();
        features.put("http://apache.org/xml/features/disallow-doctype-decl", Boolean.TRUE);
        features.put("http://apache.org/xml/features/validation/schema/normalized-value", Boolean.FALSE);
        features.put("http://javax.xml.XMLConstants/feature/secure-processing", Boolean.TRUE);
        features.put("http://xml.org/sax/features/external-general-entities", Boolean.FALSE);
        features.put("http://xml.org/sax/features/external-parameter-entities", Boolean.FALSE);

        parserPool.setBuilderFeatures(features);

        try {
            parserPool.initialize();
        } catch (final ComponentInitializationException e) {
            throw new EidasClientException("Error initializing parserPool", e);
        }
        try {
            InitializationService.initialize();
            AlgorithmSupport.getGlobalAlgorithmRegistry().register(new OpenSAMLConfiguration.SignatureRSASHA256MGF1());
        } catch (final InitializationException e) {
            throw new EidasClientException("Error initializing OpenSAML", e);
        }

        XMLObjectProviderRegistry registry;
        synchronized(ConfigurationService.class) {
            registry = ConfigurationService.get(XMLObjectProviderRegistry.class);
            if (registry == null) {
                registry = new XMLObjectProviderRegistry();
                ConfigurationService.register(XMLObjectProviderRegistry.class, registry);
            }
        }
        registry.setParserPool(parserPool);
    }

    public static final class SignatureRSASHA256MGF1 implements SignatureAlgorithm {
        public SignatureRSASHA256MGF1() {
        }

        @Nonnull
        public String getKey() {
            return "RSA";
        }

        @Nonnull
        public String getURI() {
            return "http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1";
        }

        @Nonnull
        public AlgorithmType getType() {
            return AlgorithmType.Signature;
        }

        @Nonnull
        public String getJCAAlgorithmID() {
            return "SHA256withRSAandMGF1";
        }

        @Nonnull
        public String getDigest() {
            return "SHA-256";
        }
    }
}