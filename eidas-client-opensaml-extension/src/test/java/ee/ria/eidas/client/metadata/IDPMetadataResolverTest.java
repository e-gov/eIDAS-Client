package ee.ria.eidas.client.metadata;

import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.InputStream;

@TestPropertySource(locations="classpath:application-test.properties")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = EidasClientConfiguration.class)
public class IDPMetadataResolverTest {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Autowired
    private ExplicitKeySignatureTrustEngine metadataSignatureTrustEngine;

    @Test
    public void resolveValidIdpMetadata() throws Exception {
        InputStream metadataInputStream = getClass().getResourceAsStream("/idp-metadata.xml");
        IDPMetadataResolver idpMetadataResolver = new IDPMetadataResolver(metadataInputStream, metadataSignatureTrustEngine);
        MetadataResolver metadataResolver = idpMetadataResolver.resolve();
        Assert.assertNotNull(metadataResolver);
        Assert.assertTrue(metadataResolver.isRequireValidMetadata());
        Assert.assertEquals("http://localhost:8080/EidasNode/ConnectorResponderMetadata", metadataResolver.resolve(new CriteriaSet(new EntityIdCriterion("http://localhost:8080/EidasNode/ConnectorResponderMetadata"))).iterator().next().getEntityID());
    }

    @Test
    public void resolveIdpMetadataWithInvalidSignature() {
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("No valid EntityDescriptors found!");

        InputStream metadataInputStream = getClass().getResourceAsStream("/idp-metadata-invalid_signature.xml");
        IDPMetadataResolver idpMetadataResolver = new IDPMetadataResolver(metadataInputStream, metadataSignatureTrustEngine);
        idpMetadataResolver.resolve();
        Assert.fail("Test should not reach this!");
    }

    // TODO expired metadata
    // TODO expired certificate
}

