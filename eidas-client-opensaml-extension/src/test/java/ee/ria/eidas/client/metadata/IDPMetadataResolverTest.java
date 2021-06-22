package ee.ria.eidas.client.metadata;

import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.config.EidasCredentialsConfiguration;
import ee.ria.eidas.client.exception.EidasClientException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

@TestPropertySource(locations = "classpath:application-test.properties")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { EidasClientConfiguration.class, EidasCredentialsConfiguration.class })
public class IDPMetadataResolverTest {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Autowired
    private ExplicitKeySignatureTrustEngine idpMetadataSignatureTrustEngine;

    @Mock
    @Autowired
    private EidasClientProperties eidasClientProperties;

    @InjectMocks
    private IDPMetadataResolver idpMetadataResolver;

    @Before
    public void initMocks() {
        idpMetadataResolver = new IDPMetadataResolver("classpath:idp-metadata.xml", idpMetadataSignatureTrustEngine);
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void resolveSuccessfullyFromClasspath() throws Exception {
        IDPMetadataResolver idpMetadataResolver = new IDPMetadataResolver("classpath:idp-metadata.xml", idpMetadataSignatureTrustEngine);
        MetadataResolver metadataResolver = idpMetadataResolver.resolve();
        Assert.assertNotNull(metadataResolver);
        Assert.assertTrue(metadataResolver.isRequireValidMetadata());
        assertEquals("classpath:idp-metadata.xml", metadataResolver.resolveSingle(new CriteriaSet(new EntityIdCriterion("classpath:idp-metadata.xml"))).getEntityID());
    }

    @Test
    public void resolveFailsWhenUrlNotSet() throws Exception {
        expectedEx.expect(EidasClientException.class);
        expectedEx.expectMessage("Idp metadata resource not set! Please check your configuration.");

        assertResolveFails(null);
    }

    @Test
    public void resolveFailsWhenClasspathMetadataResourceNotFound() throws Exception {
        expectedEx.expect(EidasClientException.class);
        expectedEx.expectMessage("Error resolving IDP Metadata");

        assertResolveFails("classpath:nonexisting.xml");
    }

    @Test
    public void resolveFailsWhenUrlMetadataResourceNotFound() throws Exception {
        expectedEx.expect(EidasClientException.class);
        expectedEx.expectMessage("Error initializing IDP Metadata provider.");

        assertResolveFails("http://0.0.0.0/metadata");
    }

    @Test
    public void resolveFailsWhenIdpMetadataWithInvalidSignature() {
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("No valid EntityDescriptor with entityID = 'classpath:idp-metadata-invalid_signature.xml' was found!");

        assertResolveFails("classpath:idp-metadata-invalid_signature.xml");
    }

    @Test
    public void resolveFailsWhenIdpMetadataHasExpired() {
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("No valid EntityDescriptor with entityID = 'classpath:idp-metadata-expired.xml' was found!");

        assertResolveFails("classpath:idp-metadata-expired.xml");
    }

    @Test
    public void resolveFailsWhenXmlContainingDoctypesAreReturned() {
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("Error initializing IDP Metadata provider.");

        assertResolveFails("classpath:idp-metadata-xxe.xml");
    }

    @Test
    public void getSupportedCountriesSuccessfullyFromConfigurationIfEntityDescriptorMissing() {
        assertEquals(new ArrayList<>(Collections.emptyList()), idpMetadataResolver.getSupportedCountries(null));
    }

    @Test
    public void getSupportedCountriesSuccessfullyFromConfigurationIfNoneSpecifiedInMetadata() {
        Mockito.when(eidasClientProperties.getAvailableCountries()).thenReturn(new ArrayList<>(Arrays.asList("EE", "CA")));
        assertEquals(new ArrayList<>(Arrays.asList("EE", "CA")), idpMetadataResolver.getSupportedCountries());
    }

    private void assertResolveFails(String url) {
        IDPMetadataResolver idpMetadataResolver = new IDPMetadataResolver(url, idpMetadataSignatureTrustEngine);
        MetadataResolver metadataResolver = idpMetadataResolver.resolve();
        Assert.fail("Test should not reach this!");
    }
}

