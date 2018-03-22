package ee.ria.eidas.client.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.Response;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(Include.NON_NULL)
public class AuthenticationResult {

    private String levelOfAssurance;

    private Map<String, String> attributes = new HashMap<>();

    public AuthenticationResult(Assertion assertion) {
        levelOfAssurance = assertion.getAuthnStatements().get(0).getAuthnContext().getAuthnContextClassRef().getAuthnContextClassRef();
        for (Attribute attribute : assertion.getAttributeStatements().get(0).getAttributes()) {
            attributes.put(attribute.getFriendlyName(), attribute.getAttributeValues().get(0).getDOM().getTextContent());
        }
    }

    public String getLevelOfAssurance() {
        return levelOfAssurance;
    }

    public void setLevelOfAssurance(String levelOfAssurance) {
        this.levelOfAssurance = levelOfAssurance;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

}
