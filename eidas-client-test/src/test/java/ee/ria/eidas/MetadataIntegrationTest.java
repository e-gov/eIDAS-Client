package ee.ria.eidas;


import static io.restassured.path.xml.config.XmlPathConfig.xmlPathConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.restassured.path.xml.XmlPath;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class MetadataIntegrationTest extends TestsBase {
    @Ignore
    @Test
    public  void metap1_hasValidSignature() {
        assertTrue(validateMetadataSignature(getMedatadaBody()));
    }
    @Ignore
    @Test
    public void metap2_verifySamlMetadataSchema() {
        assertTrue(validateMetadataSchema());
    }

    @Ignore //Not needed, because we have schema check?
    @Test
    public void metap2_verifySamlMetadataIdentifier() {
        String response = getMedatadaBody();
        XmlPath xmlPath = new XmlPath(response).using(xmlPathConfig().namespaceAware(false));
        assertEquals("The namespace should be expected", "urn:oasis:names:tc:SAML:2.0:metadata2", xmlPath.getString("EntityDescriptor.@xmlns:md"));
    }
    @Ignore
    @Test
    public void metap3_mandatoryValuesArePresentInEntityDescriptor() {
        Instant currentTime = Instant.now();
        String response = getMedatadaBody();
        XmlPath xmlPath = new XmlPath(response);
        assertEquals("The entityID must be the same as entpointUrl", SP_URL+":"+SP_PORT+SP_METADATA_ENDPOINT, xmlPath.getString("EntityDescriptor.@entityID"));

        Instant validUntil = Instant.parse(xmlPath.getString("EntityDescriptor.@validUntil"));
        assertThat("The metadata should be valid for 24h",currentTime.plus(Duration.ofHours(23).plusMinutes(50)), lessThan(validUntil));
    }
    @Ignore
    @Test
    public void metap3_mandatoryValuesArePresentInExtensions() {
        String response = getMedatadaBody();
        XmlPath xmlPath = new XmlPath(response);
        assertEquals("ServiceProvider should be public", "public", xmlPath.getString("EntityDescriptor.Extensions.SPType"));

        List<String> digestMethods = xmlPath.getList("EntityDescriptor.Extensions.DigestMethod.@Algorithm");
        assertThat("one of the accepted digest algorithms must be present", digestMethods,
                anyOf(hasItem("http://www.w3.org/2001/04/xmlenc#sha512"), hasItem("http://www.w3.org/2001/04/xmlenc#sha256")));

        List<String> signingMethods = xmlPath.getList("EntityDescriptor.Extensions.SigningMethod.@Algorithm");
        assertThat("one of the accepted singing algorithms must be present", signingMethods,
                anyOf(hasItem("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512"), hasItem("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256"),
                        hasItem("http://www.w3.org/2007/05/xmldsig-more#sha512-rsa-MGF1"), hasItem("http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1")));

    }
}
