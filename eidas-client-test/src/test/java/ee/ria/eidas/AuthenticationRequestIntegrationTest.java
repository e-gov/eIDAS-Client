package ee.ria.eidas;


import ee.ria.eidas.config.IntegrationTest;
import io.restassured.path.xml.XmlPath;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;

@Category(IntegrationTest.class)
public class AuthenticationRequestIntegrationTest extends TestsBase {

    @Value("${eidas.client.idpStartUrl}")
    private String idpStartUrl;

    @Value("${eidas.client.spProviderName}")
    private String spProviderName;

    @Value("${eidas.client.spStartUrl}")
    private String spStartUrl;

    @Value("${eidas.client.spReturnUrl}")
    private String spReturnUrl;

    @Test
    public void auth6_allLoaLevelsAreAccepted() {
        XmlPath html = new XmlPath(XmlPath.CompatibilityMode.HTML, getAuthenticationReq("EE", "LOW", "relayState"));
        assertEquals("Country code is present","EE", html.getString("**.findAll { it.@name == 'country' }.@value"));
        assertEquals("RelayState is present","relayState", html.getString("**.findAll { it.@name == 'RelayState' }.@value"));
    }

    @Test
    public void auth6_parametersArePresent() {
        XmlPath samlRequest = getDecodedSamlRequestBodyXml(getAuthenticationReq("EE", "LOW", "relayState"));
        assertEquals("Country code is present","http://eidas.europa.eu/LoA/low", samlRequest.getString("AuthnRequest.RequestedAuthnContext.AuthnContextClassRef"));

        samlRequest = getDecodedSamlRequestBodyXml(getAuthenticationReq("EE", "SUBSTANTIAL", "relayState"));
        assertEquals("Country code is present","http://eidas.europa.eu/LoA/substantial", samlRequest.getString("AuthnRequest.RequestedAuthnContext.AuthnContextClassRef"));

        samlRequest = getDecodedSamlRequestBodyXml(getAuthenticationReq("EE", "HIGH", "relayState"));
        assertEquals("Country code is present","http://eidas.europa.eu/LoA/high", samlRequest.getString("AuthnRequest.RequestedAuthnContext.AuthnContextClassRef"));
    }

    @Test
    public void auth6_invalidParametersAreNotBlocking() {
        Map<String,String> formParams = new HashMap<String,String>();
        formParams.put("randomParam", "random");
        formParams.put("loa", "LOW");
        formParams.put("country", "EE");
        formParams.put("relayState", "1234abcd");

        XmlPath html = new XmlPath(XmlPath.CompatibilityMode.HTML, getAuthenticationReqForm(formParams));
        assertEquals("Status is returned with correct relayState","1234abcd", html.getString("**.findAll { it.@name == 'RelayState' }.@value"));
    }

    @Test
    public void auth9_loginPageIsDisplayed() {
        XmlPath html = new XmlPath(XmlPath.CompatibilityMode.HTML, getLoginPage());
        assertEquals("Login page is loaded", "eIDAS Client Login", html.getString("html.body.div.div.h1"));
    }


}
