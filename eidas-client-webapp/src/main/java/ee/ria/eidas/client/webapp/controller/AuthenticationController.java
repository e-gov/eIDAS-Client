package ee.ria.eidas.client.webapp.controller;

import ee.ria.eidas.client.AuthInitiationService;
import ee.ria.eidas.client.AuthResponseService;
import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.response.AuthenticationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static ee.ria.eidas.client.webapp.EidasClientApi.ENDPOINT_AUTHENTICATION_LOGIN;
import static ee.ria.eidas.client.webapp.EidasClientApi.ENDPOINT_AUTHENTICATION_RETURN_URL;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@Controller
public class AuthenticationController {

    @Autowired
    private AuthInitiationService authInitiationService;

    @Autowired
    private AuthResponseService authResponseService;

    @Autowired
    private EidasClientProperties properties;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @RequestMapping(value = ENDPOINT_AUTHENTICATION_LOGIN, method = GET)
    public void authenticate(HttpServletResponse response,
            @RequestParam("Country") String country,
            @RequestParam(value = "LoA", required=false) AssuranceLevel loa,
            @RequestParam(value = "RelayState", required=false) String relayState,
            @RequestParam(value = "Attributes", required=false) String eidasAttributes) {
        authInitiationService.authenticate(response, country, loa, relayState, eidasAttributes);
    }

    @RequestMapping(value = ENDPOINT_AUTHENTICATION_RETURN_URL, method = POST)
    @ResponseBody
    public AuthenticationResult getAuthenticationResult(HttpServletRequest req) throws MissingServletRequestParameterException {
        return authResponseService.getAuthenticationResult(req);
    }

}
