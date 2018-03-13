package ee.ria.eidas.client.webapp.controller;

import ee.ria.eidas.client.AuthInitiationService;
import ee.ria.eidas.client.AuthResponseService;
import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.response.AuthenticationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

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

    @RequestMapping(value = {"/login", "/"}, method = GET)
    public String authenticate(Model model) {
        List<AssuranceLevel> levelsOfAssurance = Arrays.asList(AssuranceLevel.values());
        model.addAttribute("countries", properties.getAvailableCountries());
        model.addAttribute("loas", levelsOfAssurance);
        return "login";
    }

    @RequestMapping(value = "/login", method = POST)
    public void authenticate(HttpServletResponse response,
            @RequestParam("country") String country,
            @RequestParam(value = "loa", required=false) AssuranceLevel loa,
            @RequestParam(value = "relayState", required=false) String relayState) {
        authInitiationService.authenticate(response, country, loa, relayState);
    }

    @RequestMapping(value = "/returnUrl", method = POST)
    @ResponseBody
    public AuthenticationResult getAuthenticationResult(HttpServletRequest req) {
        return authResponseService.getAuthenticationResult(req);
    }

}
