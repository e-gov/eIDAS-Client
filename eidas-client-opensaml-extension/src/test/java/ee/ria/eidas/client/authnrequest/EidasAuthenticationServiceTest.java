package ee.ria.eidas.client.authnrequest;

import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.EidasClientException;
import net.shibboleth.utilities.java.support.codec.HTMLEncoder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.credential.Credential;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@TestPropertySource(locations="classpath:application-test.properties")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = EidasClientConfiguration.class)
public class EidasAuthenticationServiceTest {

    @Autowired
    private EidasClientProperties properties;

    @Autowired
    private Credential authnReqSigningCredential;

    @Autowired
    private SingleSignOnService singleSignOnService;

    private EidasAuthenticationService authenticationService;

    @Before
    public void setUp() {
        authenticationService = new EidasAuthenticationService(authnReqSigningCredential, properties, singleSignOnService);
    }

    @Test
    public void returnsHttpPostBindingResponse() throws Exception {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        authenticationService.authenticate(httpRequest, httpResponse, "EE", AssuranceLevel.LOW, "test");

        assertEquals(HttpStatus.OK.value(), httpResponse.getStatus());
        String htmlForm = "<form action=\"" + HTMLEncoder.encodeForHTMLAttribute(singleSignOnService.getLocation()) + "\" method=\"post\">";
        assertTrue(httpResponse.getContentAsString().contains(htmlForm));
        assertTrue(httpResponse.getContentAsString().contains("<input type=\"hidden\" name=\"SAMLRequest\""));
        assertTrue(httpResponse.getContentAsString().contains("<input type=\"hidden\" name=\"country\" value=\"EE\"/>"));
        assertTrue(httpResponse.getContentAsString().contains("<input type=\"hidden\" name=\"RelayState\" value=\"test\"/>"));
    }

    @Test(expected = EidasClientException.class)
    public void invalidRelayState_throwsException() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        authenticationService.authenticate(httpRequest, httpResponse, "EE", AssuranceLevel.LOW, "ä");
    }

    @Test(expected = EidasClientException.class)
    public void invalidCountry_throwsException() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        authenticationService.authenticate(httpRequest, httpResponse, "NEVERLAND", AssuranceLevel.LOW, "test");
    }

}
