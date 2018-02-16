package ee.ria.eidas.client.webapp.controller;

import ee.ria.eidas.client.metadata.SPMetadataGenerator;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@Controller
public class ViewController {

    @Autowired
    private SPMetadataGenerator metadataGenerator;

    @RequestMapping(value = "/metadata", method = GET, produces = { "application/xml", "text/xml" }, consumes = MediaType.ALL_VALUE)
    public @ResponseBody String view() {
        EntityDescriptor entityDescriptor = metadataGenerator.getMetadata();
        return OpenSAMLUtils.getXmlString(entityDescriptor);
    }

    @RequestMapping(value = "/start", method = GET)
    public String view(Model model) {
        model.addAttribute("hello", "Hello to protected world!");
        return "view";
    }
}
