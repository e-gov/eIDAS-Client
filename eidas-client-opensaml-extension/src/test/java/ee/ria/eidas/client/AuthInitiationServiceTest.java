package ee.ria.eidas.client;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.AuthnRequestBuilder;
import ee.ria.eidas.client.authnrequest.EidasAttribute;
import ee.ria.eidas.client.config.EidasClientConfiguration;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.metadata.IDPMetadataResolver;
import ee.ria.eidas.client.session.RequestSessionService;
import net.shibboleth.utilities.java.support.codec.HTMLEncoder;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.bouncycastle.util.encoders.Base64;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.schema.impl.XSAnyImpl;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Extensions;
import org.opensaml.security.credential.Credential;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@TestPropertySource(locations = "classpath:application-test.properties")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = EidasClientConfiguration.class)
public class AuthInitiationServiceTest {

    @Autowired
    private RequestSessionService requestSessionService;

    @Autowired
    private EidasClientProperties properties;

    @Autowired
    private Credential authnReqSigningCredential;

    @Autowired
    private IDPMetadataResolver idpMetadataResolver;

    private AuthInitiationService authenticationService;

    @Before
    public void setUp() {
        authenticationService = new AuthInitiationService(requestSessionService, authnReqSigningCredential, properties, idpMetadataResolver);
    }

    @Test
    public void returnsHttpPostBindingResponseWithDefaultNaturalHumanRequestAttributesIfNoAttributesArePassedFromRequest() throws Exception {
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        authenticationService.authenticate(httpResponse, "EE", AssuranceLevel.LOW, "test", null);

        assertEquals(HttpStatus.OK.value(), httpResponse.getStatus());
        String htmlForm = "<form action=\"" + HTMLEncoder.encodeForHTMLAttribute(idpMetadataResolver.getSingeSignOnService().getLocation()) + "\" method=\"post\">";
        String responseContent = httpResponse.getContentAsString();
        assertTrue(responseContent.contains(htmlForm));
        assertTrue(responseContent.contains("<input type=\"hidden\" name=\"SAMLRequest\""));
        assertTrue(responseContent.contains("<input type=\"hidden\" name=\"country\" value=\"EE\"/>"));
        assertTrue(responseContent.contains("<input type=\"hidden\" name=\"RelayState\" value=\"test\"/>"));

        assertRequestedAttributesInSamlRequest(responseContent, AuthInitiationService.DEFAULT_REQUESTED_ATTRIBUTE_SET);
    }

    @Test
    public void returnsExactRequestAttributesThatArePassedFromRequest() throws Exception {
        List<EidasAttribute> requestEidasAttributes = Arrays.asList(EidasAttribute.LEGAL_PERSON_IDENTIFIER, EidasAttribute.LEGAL_NAME);
        String eidasAttributesSet = requestEidasAttributes.stream().map(EidasAttribute::getFriendlyName).collect(Collectors.joining(" "));

        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        authenticationService.authenticate(httpResponse, "EE", AssuranceLevel.LOW, "test", eidasAttributesSet);

        assertEquals(HttpStatus.OK.value(), httpResponse.getStatus());
        String responseContent = httpResponse.getContentAsString();
        assertRequestedAttributesInSamlRequest(responseContent, requestEidasAttributes);
    }

    @Test
    public void allEidasAttributesRequested() throws Exception {
        List<EidasAttribute> requestEidasAttributes = Arrays.asList(EidasAttribute.values());
        String eidasAttributesSet = requestEidasAttributes.stream().map(EidasAttribute::getFriendlyName).collect(Collectors.joining(" "));

        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        authenticationService.authenticate(httpResponse, "EE", AssuranceLevel.LOW, "test", eidasAttributesSet);

        assertEquals(HttpStatus.OK.value(), httpResponse.getStatus());
        String responseContent = httpResponse.getContentAsString();
        assertRequestedAttributesInSamlRequest(responseContent, requestEidasAttributes);
    }

    @Test(expected = EidasClientException.class)
    public void invalidRelayState_throwsException() throws ParserConfigurationException, SAXException, IOException, ResolverException {
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        authenticationService.authenticate(httpResponse, "EE", AssuranceLevel.LOW, "ä", null);
    }

    @Test(expected = EidasClientException.class)
    public void invalidCountry_throwsException() throws ParserConfigurationException, SAXException, IOException, ResolverException {
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        authenticationService.authenticate(httpResponse, "NEVERLAND", AssuranceLevel.LOW, "test", null);
    }

    private void assertRequestedAttributesInSamlRequest(String responseContent, List<EidasAttribute> eidasAttributes) throws Exception {
        String samlRequest = parseSamlRequest(responseContent);
        List<XMLObject> requestedAttributes = parseRequestedAttributesFromSamlRequest(samlRequest);
        assertSame(eidasAttributes.size(), requestedAttributes.size());

        for (int i = 0; i < eidasAttributes.size(); i++) {
            List<String> requestAttributeValues = new ArrayList<>(((XSAnyImpl) requestedAttributes.get(0)).getUnknownAttributes().values());
            String friendlyName = requestAttributeValues.get(2);
            EidasAttribute parsedEidasAttribute = EidasAttribute.fromString(friendlyName);
            assertTrue(eidasAttributes.contains(parsedEidasAttribute));
            assertEquals(parsedEidasAttribute.isRequired() ? "true" : "false", requestAttributeValues.get(0));
            assertEquals(AuthnRequestBuilder.REQUESTED_ATTRIBUTE_NAME_FORMAT, requestAttributeValues.get(1));
            assertEquals(parsedEidasAttribute.getFriendlyName(), friendlyName);
            assertEquals(parsedEidasAttribute.getName(), requestAttributeValues.get(3));
        }
    }

    private String parseSamlRequest(String responseContent) {
        int samlRequestStartIndex = responseContent.indexOf("SAMLRequest\" value=\"") + 20;
        int samlRequestEndIndex = responseContent.indexOf("\"", samlRequestStartIndex);
        return responseContent.substring(samlRequestStartIndex, samlRequestEndIndex);
    }

    private List<XMLObject> parseRequestedAttributesFromSamlRequest(String samlRequest) throws Exception {
        InitializationService.initialize();
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();

        Document document = docBuilder.parse(new ByteArrayInputStream(Base64.decode(samlRequest)));
        Element element = document.getDocumentElement();

        UnmarshallerFactory unmarshallerFactory = XMLObjectProviderRegistrySupport.getUnmarshallerFactory();
        Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(document.getDocumentElement());

        AuthnRequest authnRequest = (AuthnRequest) unmarshaller.unmarshall(element);
        Extensions extensions = authnRequest.getExtensions();
        XMLObject requestedAttributes = extensions.getOrderedChildren().get(1);
        return requestedAttributes.getOrderedChildren();
    }
}
