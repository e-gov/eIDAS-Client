package ee.ria.eidas.client.webapp.controller;

import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.metadata.SPMetadataGenerator;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

import static ee.ria.eidas.client.webapp.EidasClientApi.ENDPOINT_METADATA_METADATA;
import static ee.ria.eidas.client.webapp.EidasClientApi.ENDPOINT_METADATA_SUPPORTED_COUNTRIES;

@Controller
public class MetadataController {

    @Autowired
    private EidasClientProperties eidasClientProperties;

    @Autowired
    private SPMetadataGenerator metadataGenerator;

    @GetMapping(value = ENDPOINT_METADATA_METADATA, produces = { "application/xml", "text/xml" }, consumes = MediaType.ALL_VALUE)
    public @ResponseBody String metadata() {
        EntityDescriptor entityDescriptor = metadataGenerator.getMetadata();
        return OpenSAMLUtils.getXmlString(entityDescriptor);
    }

    @GetMapping(value = ENDPOINT_METADATA_SUPPORTED_COUNTRIES, produces = { "application/json" }, consumes = MediaType.ALL_VALUE)
    public @ResponseBody List<String> countries() {
        return eidasClientProperties.getAvailableCountries();
    }
}
