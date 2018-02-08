package ee.ria.eidas.client.webapp.controller;

import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.io.MarshallerFactory;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.w3c.dom.Element;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@Controller
public class ViewController {

    @Autowired
    private SAML2Client saml2Client;

    @RequestMapping(value = "/", method = GET)
    public String view(Model model) {
        model.addAttribute("hello", "Hello world!");
        return "view";
    }

    @RequestMapping(value = "/metadata", method = GET, produces = { "application/xml", "text/xml" }, consumes = MediaType.ALL_VALUE)
    public @ResponseBody String viewMetadata(Model model) {
        return saml2Client.getServiceProviderMetadataResolver().getMetadata();
    }
}
