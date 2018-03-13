package ee.ria.eidas.client.metadata;

import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.exception.EidasClientException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@TestPropertySource(locations = "classpath:application-test.properties")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = EidasClientConfiguration.class)
public class IDPMetadataResolverTest {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Autowired
    private ExplicitKeySignatureTrustEngine metadataSignatureTrustEngine;

    @Autowired
    private ResourceLoader resourceLoader;

    @Test
    public void resolveValidIdpMetadata() throws Exception {
        IDPMetadataResolver idpMetadataResolver = new IDPMetadataResolver("classpath:idp-metadata.xml", metadataSignatureTrustEngine);
        MetadataResolver metadataResolver = idpMetadataResolver.resolve();
        Assert.assertNotNull(metadataResolver);
        Assert.assertTrue(metadataResolver.isRequireValidMetadata());
        Assert.assertEquals("http://localhost:8080/EidasNode/ConnectorResponderMetadata", metadataResolver.resolve(new CriteriaSet(new EntityIdCriterion("http://localhost:8080/EidasNode/ConnectorResponderMetadata"))).iterator().next().getEntityID());
    }

    @Test
    public void resolveNonexistingMetadata() throws Exception {
        expectedEx.expect(EidasClientException.class);
        expectedEx.expectMessage("Idp metadata resource not set! Please check your configuration.");

        assertResolveFails(null);
    }

    @Test
    public void resolveIdpMetadataWithInvalidSignature() {
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("No valid EntityDescriptors found!");

        assertResolveFails("classpath:idp-metadata-invalid_signature.xml");
    }

    @Test
    public void resolveIdpMetadataHasExpired() {
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("No valid EntityDescriptors found!");

        assertResolveFails("classpath:idp-metadata-expired.xml");
    }

    private void assertResolveFails(String url) {
        IDPMetadataResolver idpMetadataResolver = new IDPMetadataResolver(url, metadataSignatureTrustEngine);
        MetadataResolver metadataResolver = idpMetadataResolver.resolve();
        Assert.fail("Test should not reach this!");
    }

    // TODO expired certificate
}

