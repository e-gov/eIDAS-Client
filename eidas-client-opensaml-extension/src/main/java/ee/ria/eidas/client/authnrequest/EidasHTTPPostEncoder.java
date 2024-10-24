package ee.ria.eidas.client.authnrequest;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder;

public class EidasHTTPPostEncoder extends HTTPPostEncoder {

    private String countryCode;
    private String relayState;

    public EidasHTTPPostEncoder() {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocityEngine.init();
        setVelocityEngine(velocityEngine);
        setVelocityTemplateId("/templates/eidas-saml2-post-binding.vm");
    }

    @Override
    protected void populateVelocityContext(VelocityContext velocityContext, MessageContext messageContext, String endpointURL) throws MessageEncodingException {
        velocityContext.put("Country", countryCode);
        velocityContext.put("RelayState", relayState);
        super.populateVelocityContext(velocityContext, messageContext, endpointURL);
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public void setRelayState(String relayState) {
        this.relayState = relayState;
    }
}
