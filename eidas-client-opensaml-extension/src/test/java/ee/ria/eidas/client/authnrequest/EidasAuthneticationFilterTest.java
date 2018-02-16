package ee.ria.eidas.client.authnrequest;

import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import net.shibboleth.utilities.java.support.codec.HTMLEncoder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
public class EidasAuthneticationFilterTest {

    @Autowired
    private EidasClientProperties properties;

    @Autowired
    private Credential authnReqSigningCredential;

    private EidasAuthenticationFilter authenticationFilter;

    @Before
    public void setUp() {
        authenticationFilter = new EidasAuthenticationFilter(authnReqSigningCredential, properties);
    }

    @Test
    public void returnsHttpPostBindingResponse() throws Exception {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        authenticationFilter.doFilter(httpRequest, httpResponse, filterChain);

        assertEquals(HttpStatus.OK.value(), httpResponse.getStatus());
        String htmlForm = "<form action=\"" + HTMLEncoder.encodeForHTMLAttribute(properties.getIdpSSOUrl()) + "\" method=\"post\">";
        assertTrue(httpResponse.getContentAsString().contains(htmlForm));
        assertTrue(httpResponse.getContentAsString().contains("<input type=\"hidden\" name=\"SAMLRequest\""));
        assertTrue(httpResponse.getContentAsString().contains("<input type=\"hidden\" name=\"country\" value=\"CA\"/>"));
    }

}
