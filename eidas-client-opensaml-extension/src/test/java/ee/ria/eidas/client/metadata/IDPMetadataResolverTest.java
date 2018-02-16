package ee.ria.eidas.client.metadata;

import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.InputStream;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = EidasClientConfiguration.class)
@TestPropertySource(locations="classpath:application-test.properties")
public class IDPMetadataResolverTest {

    @Autowired
    private EidasClientProperties properties;

    private IDPMetadataResolver idpMetadataResolver;

    @Before
    public void setUp() {
        InputStream metadataInputStream = getClass().getResourceAsStream(properties.getIdpMetadataUrl());
        idpMetadataResolver = new IDPMetadataResolver(metadataInputStream);
    }

    @Test
    public void test() {
        // TODO
        MetadataResolver resolver = idpMetadataResolver.resolve();
    }

}

