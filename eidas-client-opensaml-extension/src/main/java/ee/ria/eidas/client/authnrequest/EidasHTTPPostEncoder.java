package ee.ria.eidas.client.authnrequest;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EidasHTTPPostEncoder extends HTTPPostEncoder {

    private static Logger LOGGER = LoggerFactory.getLogger(org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder.class);

    private String countryCode;
    private String relayState;

    public EidasHTTPPostEncoder() {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocityEngine.init();
        setVelocityEngine(velocityEngine);
    }

    @Override
    protected void populateVelocityContext(VelocityContext velocityContext, MessageContext<SAMLObject> messageContext, String endpointURL) throws MessageEncodingException {
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