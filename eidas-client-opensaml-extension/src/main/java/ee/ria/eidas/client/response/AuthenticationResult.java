package ee.ria.eidas.client.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeValue;
import org.opensaml.saml.saml2.core.Response;
import org.springframework.util.CollectionUtils;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(Include.NON_NULL)
public class AuthenticationResult {

    private String levelOfAssurance;

    private Map<String, String> attributes = new HashMap<>();

    @JsonInclude(Include.NON_EMPTY)
    private Map<String, String> attributesNonLatin = new HashMap<>();

    public AuthenticationResult(Assertion assertion) {
        levelOfAssurance = assertion.getAuthnStatements().get(0).getAuthnContext().getAuthnContextClassRef().getAuthnContextClassRef();
        for (Attribute attribute : assertion.getAttributeStatements().get(0).getAttributes()) {
            for (XMLObject attributeValue : attribute.getAttributeValues()) {
                setResponseAttribute(attribute, (XSAny) attributeValue);
            }
        }
    }

    private void setResponseAttribute(Attribute attribute, XSAny attributeValue) {
        if (isNonLatin(attributeValue)) {
            attributesNonLatin.put(attribute.getFriendlyName(), attributeValue.getTextContent());
        } else {
            attributes.put(attribute.getFriendlyName(), attributeValue.getTextContent());
        }
    }

    private boolean isNonLatin(XSAny a) {
        QName nsp = new QName("http://eidas.europa.eu/attributes/naturalperson", "LatinScript", "eidas-natural");
        return !CollectionUtils.isEmpty(a.getUnknownAttributes()) && a.getUnknownAttributes().containsKey(nsp) && a.getUnknownAttributes().get(nsp).equals("false");
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

    public Map<String, String> getAttributesNonLatin() {
        return attributesNonLatin;
    }

    public void setAttributesNonLatin(Map<String, String> attributesNonLatin) {
        this.attributesNonLatin = attributesNonLatin;
    }
}
