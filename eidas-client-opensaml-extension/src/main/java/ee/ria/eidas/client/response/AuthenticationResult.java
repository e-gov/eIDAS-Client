package ee.ria.eidas.client.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonInclude(Include.NON_NULL)
public class AuthenticationResult {

    private String levelOfAssurance;

    private Map<String, String> attributes = new HashMap<>();

    @JsonInclude(Include.NON_EMPTY)
    private Map<String, String> attributesTransliterated = new HashMap<>();

    public AuthenticationResult(Assertion assertion) {
        levelOfAssurance = assertion.getAuthnStatements().get(0).getAuthnContext().getAuthnContextClassRef().getURI();
        for (Attribute attribute : assertion.getAttributeStatements().get(0).getAttributes()) {
            for (XMLObject attributeValue : attribute.getAttributeValues()) {
                addToResponse(attribute, (XSAny) attributeValue);
            }
        }
    }

    private void addToResponse(Attribute attribute, XSAny attributeValue) {
        if (isTransliteratedAttribute(attribute)) {
            addTransliteratedValues(attribute, attributeValue);
        } else {
            attributes.put(attribute.getFriendlyName(), attributeValue.getTextContent());
        }
    }

    private boolean isTransliteratedAttribute(Attribute attribute) {
        return attribute.getAttributeValues().size() == 2 && isLatinscriptAttributePresent(attribute);
    }

    private boolean isLatinscriptAttributePresent(Attribute attribute) {
        for (XMLObject attributeValue: attribute.getAttributeValues()) {
            List<QName> latinScriptAttributes = ((XSAny)attributeValue).getUnknownAttributes().keySet().stream().filter(x -> x.getLocalPart().equals("LatinScript")).collect(Collectors.toList());
            if (!latinScriptAttributes.isEmpty())
                return true;
        }

        return false;
    }

    private void addTransliteratedValues(Attribute attribute, XSAny attributeValue) {
        if (isLatinScript(attributeValue)) {
            attributesTransliterated.put(attribute.getFriendlyName(), attributeValue.getTextContent());
        } else {
            attributes.put(attribute.getFriendlyName(), attributeValue.getTextContent());
        }
    }

    private boolean isLatinScript(XSAny a) {
        List<QName> latinScriptAttributes = a.getUnknownAttributes().keySet().stream().filter(x -> x.getLocalPart().equals("LatinScript")).collect(Collectors.toList());
        if (latinScriptAttributes.isEmpty()) {
            return true;
        } else if (latinScriptAttributes.size() == 1) {
            return latinScriptAttributes.get(0).toString().equals("true");
        } else {
            throw new IllegalStateException("More than one LatinScript attributes not allowed!");
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

    public Map<String, String> getAttributesTransliterated() {
        return attributesTransliterated;
    }

    public void setAttributesTransliterated(Map<String, String> attributesTransliterated) {
        this.attributesTransliterated = attributesTransliterated;
    }
}
