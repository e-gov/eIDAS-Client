package ee.ria.eidas.util;

import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import javax.servlet.http.HttpServletResponse;
import net.shibboleth.utilities.java.support.codec.Base64Support;
import net.shibboleth.utilities.java.support.codec.HTMLEncoder;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.component.ComponentSupport;
import net.shibboleth.utilities.java.support.net.HttpServletSupport;
import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.binding.SAMLBindingSupport;
import org.opensaml.saml.saml2.binding.encoding.impl.BaseSAML2MessageEncoder;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder;
import org.opensaml.saml.saml2.core.RequestAbstractType;
import org.opensaml.saml.saml2.core.StatusResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

public class CustomHTTPPostEncoder extends BaseSAML2MessageEncoder {
    public static final String DEFAULT_TEMPLATE_ID = "/templates/saml2-post-binding.vm";
    private final Logger log = LoggerFactory.getLogger(org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder.class);
    private VelocityEngine velocityEngine;
    private String velocityTemplateId;
    private String countryCode;
    private String relayState;

    public CustomHTTPPostEncoder() {
        this.setVelocityTemplateId("/templates/saml2-post-binding.vm");
    }

    public String getBindingURI() {
        return "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST";
    }

    public VelocityEngine getVelocityEngine() {
        return this.velocityEngine;
    }

    public void setVelocityEngine(VelocityEngine newVelocityEngine) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        this.velocityEngine = newVelocityEngine;
    }

    public String getVelocityTemplateId() {
        return this.velocityTemplateId;
    }

    public void setVelocityTemplateId(String newVelocityTemplateId) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        this.velocityTemplateId = newVelocityTemplateId;
    }

    protected void doDestroy() {
        this.velocityEngine = null;
        this.velocityTemplateId = null;
        super.doDestroy();
    }

    protected void doInitialize() throws ComponentInitializationException {
        super.doInitialize();
        if (this.velocityEngine == null) {
            throw new ComponentInitializationException("VelocityEngine must be supplied");
        } else if (this.velocityTemplateId == null) {
            throw new ComponentInitializationException("Velocity template id must be supplied");
        }
    }

    protected void doEncode() throws MessageEncodingException {
        MessageContext<SAMLObject> messageContext = this.getMessageContext();
        SAMLObject outboundMessage = (SAMLObject)messageContext.getMessage();
        if (outboundMessage == null) {
            throw new MessageEncodingException("No outbound SAML message contained in message context");
        } else {
            String endpointURL = this.getEndpointURL(messageContext).toString();
            this.postEncode(messageContext, endpointURL);
        }
    }

    protected void postEncode(MessageContext<SAMLObject> messageContext, String endpointURL) throws MessageEncodingException {
        this.log.debug("Invoking Velocity template to create POST body");

        try {
            VelocityContext context = new VelocityContext();
            this.populateVelocityContext(context, messageContext, endpointURL);
            HttpServletResponse response = this.getHttpServletResponse();
            HttpServletSupport.addNoCacheHeaders(response);
            HttpServletSupport.setUTF8Encoding(response);
            HttpServletSupport.setContentType(response, "text/html");
            Writer out = new OutputStreamWriter(response.getOutputStream(), "UTF-8");
            this.velocityEngine.mergeTemplate(this.velocityTemplateId, "UTF-8", context, out);
            out.flush();
        } catch (Exception var6) {
            this.log.error("Error invoking Velocity template", var6);
            throw new MessageEncodingException("Error creating output document", var6);
        }
    }

    protected void populateVelocityContext(VelocityContext velocityContext, MessageContext<SAMLObject> messageContext, String endpointURL) throws MessageEncodingException {
        String encodedEndpointURL = HTMLEncoder.encodeForHTMLAttribute(endpointURL);
        this.log.debug("Encoding action url of '{}' with encoded value '{}'", endpointURL, encodedEndpointURL);
        velocityContext.put("action", encodedEndpointURL);
        velocityContext.put("binding", this.getBindingURI());
        velocityContext.put("Country", countryCode);
        //velocityContext.put("RelayState", relayState);
        SAMLObject outboundMessage = (SAMLObject) messageContext.getMessage();
        this.log.debug("Marshalling and Base64 encoding SAML message");
        Element domMessage = this.marshallMessage(outboundMessage);

        String relayState;
        String encodedRelayState;
        try {
            relayState = SerializeSupport.nodeToString(domMessage);
            encodedRelayState = Base64Support.encode(relayState.getBytes("UTF-8"), false);
            if (outboundMessage instanceof RequestAbstractType) {
                velocityContext.put("SAMLRequest", encodedRelayState);
            } else {
                if (!(outboundMessage instanceof StatusResponseType)) {
                    throw new MessageEncodingException("SAML message is neither a SAML RequestAbstractType or StatusResponseType");
                }

                velocityContext.put("SAMLResponse", encodedRelayState);
            }
        } catch (UnsupportedEncodingException var9) {
            this.log.error("UTF-8 encoding is not supported, this VM is not Java compliant.");
            throw new MessageEncodingException("Unable to encode message, UTF-8 encoding is not supported");
        }

        relayState = SAMLBindingSupport.getRelayState(messageContext);
        if (SAMLBindingSupport.checkRelayState(relayState)) {
            encodedRelayState = HTMLEncoder.encodeForHTMLAttribute(relayState);
            this.log.debug("Setting RelayState parameter to: '{}', encoded as '{}'", relayState, encodedRelayState);
            velocityContext.put("RelayState", encodedRelayState);
        }

    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public void setRelayState(String relayState) {
        this.relayState = relayState;
    }
}