package ee.ria.eidas.client.webapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "eidas.client")
public class EidasClientProperties {

    private static final String DEFAULT_KEYSTORE = "samlKeystore.jks";
    private static final String DEFAULT_KEYSTORE_PASS = "changeit";
    private static final int DEFAULT_MAXIMUM_AUTHENTICTION_LIFETIME = 3600;
    private static final String DEFAULT_NAMEID_POLICY_FORMAT = "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified";

    private String keystore = DEFAULT_KEYSTORE;

    private String keystorePass = DEFAULT_KEYSTORE_PASS;

    @NotNull
    private String callbackUrl;

    @NotNull
    private String idpMetadataUrl;

    private int maximumAuthenticationLifetime = DEFAULT_MAXIMUM_AUTHENTICTION_LIFETIME;

    private String nameIdPolicyFormat = DEFAULT_NAMEID_POLICY_FORMAT;

    @NotNull
    private String spEntityId;

    public String getKeystore() {
        return keystore;
    }

    public void setKeystore(String keystore) {
        this.keystore = keystore;
    }

    public String getKeystorePass() {
        return keystorePass;
    }

    public void setKeystorePass(String keystorePass) {
        this.keystorePass = keystorePass;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getIdpMetadataUrl() {
        return idpMetadataUrl;
    }

    public void setIdpMetadataUrl(String idpMetadataUrl) {
        this.idpMetadataUrl = idpMetadataUrl;
    }

    public int getMaximumAuthenticationLifetime() {
        return maximumAuthenticationLifetime;
    }

    public void setMaximumAuthenticationLifetime(int maximumAuthenticationLifetime) {
        this.maximumAuthenticationLifetime = maximumAuthenticationLifetime;
    }

    public String getNameIdPolicyFormat() {
        return nameIdPolicyFormat;
    }

    public void setNameIdPolicyFormat(String nameIdPolicyFormat) {
        this.nameIdPolicyFormat = nameIdPolicyFormat;
    }

    public String getSpEntityId() {
        return spEntityId;
    }

    public void setSpEntityId(String spEntityId) {
        this.spEntityId = spEntityId;
    }
}
