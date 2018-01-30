package ee.ria.eidas.client.webapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@Controller
public class ViewController {

    @RequestMapping(value = "/", method = GET)
    public String view(Model model) {
        model.addAttribute("hello", "Hello world!");
        return "view";
    }

}
