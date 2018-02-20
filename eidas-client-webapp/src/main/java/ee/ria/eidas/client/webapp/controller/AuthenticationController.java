package ee.ria.eidas.client.webapp.controller;

import ee.ria.eidas.client.authnrequest.*;
import ee.ria.eidas.client.config.EidasClientProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@Controller
public class AuthenticationController {

    @Autowired
    private EidasAuthenticationService authenticationService;

    @Autowired
    private EidasClientProperties properties;

    @RequestMapping(value = {"/login", "/"}, method = GET)
    public String authenticate(Model model) {
        List<AssuranceLevel> levelsOfAssurance = Arrays.asList(AssuranceLevel.values());
        model.addAttribute("countries", properties.getAvailableCountries());
        model.addAttribute("loas", levelsOfAssurance);
        return "login";
    }

    @RequestMapping(value = "/login", method = POST)
    public void authenticate(HttpServletRequest request, HttpServletResponse response,
                      @RequestParam("country") String country, @RequestParam("loa") AssuranceLevel loa, @RequestParam("relayState") String relayState) {
        authenticationService.authenticate(request, response, country, loa, relayState);
    }

    @RequestMapping(value = "/view", method = GET)
    public String view(Model model, HttpServletRequest request) {
        if (request.getSession().getAttribute(EidasClientProperties.SESSION_ATTRIBUTE_USER_AUTHENTICATED) == null) {
            return "redirect:/login";
        }
        model.addAttribute("hello", "Hello to protected world!");
        return "view";
    }

}
