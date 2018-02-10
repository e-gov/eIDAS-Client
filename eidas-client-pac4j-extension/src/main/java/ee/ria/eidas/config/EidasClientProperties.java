package ee.ria.eidas.config;

import javax.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "eidas.client")
public class EidasClientProperties {

    private static final int DEFAULT_MAXIMUM_AUTHENTICTION_LIFETIME = 3600;
    private static final String DEFAULT_NAMEID_POLICY_FORMAT = "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified";
    public static final String SESSION_ATTRIBUTE_USER_AUTHENTICATED = "authenticated";
    public static final String SESSION_ATTRIBUTE_ORIGINALLY_REQUESTED_URL = "originally_requested_url";

    @NotNull
    private String keystore;

    @NotNull
    private String keystorePass;

    @NotNull
    private String metadataSigningKeyId;

    @NotNull
    private String metadataSigningKeyPass;

    @NotNull
    private String requestSigningKeyId;

    @NotNull
    private String requestSigningKeyPass;

    @NotNull
    private String responseDecryptionKeyId;

    @NotNull
    private String responseDecryptionKeyPass;

    @NotNull
    private String idpSSOUrl;

    @NotNull
    private String callbackUrl;

    @NotNull
    private String idpMetadataUrl;

    private int maximumAuthenticationLifetime = DEFAULT_MAXIMUM_AUTHENTICTION_LIFETIME;

    private String nameIdPolicyFormat = DEFAULT_NAMEID_POLICY_FORMAT;

    @NotNull
    private String spEntityId;

    private String loginUrl = "/start";
    private String samlAssertionConsumerUrl = "/returnUrl";

    @NotNull
    private String providerName;

    public String getMetadataSigningKeyId() {
        return metadataSigningKeyId;
    }

    public void setMetadataSigningKeyId(String metadataSigningKeyId) {
        this.metadataSigningKeyId = metadataSigningKeyId;
    }

    public String getMetadataSigningKeyPass() {
        return metadataSigningKeyPass;
    }

    public void setMetadataSigningKeyPass(String metadataSigningKeyPass) {
        this.metadataSigningKeyPass = metadataSigningKeyPass;
    }

    public String getRequestSigningKeyId() {
        return requestSigningKeyId;
    }

    public void setRequestSigningKeyId(String requestSigningKeyId) {
        this.requestSigningKeyId = requestSigningKeyId;
    }

    public String getRequestSigningKeyPass() {
        return requestSigningKeyPass;
    }

    public void setRequestSigningKeyPass(String requestSigningKeyPass) {
        this.requestSigningKeyPass = requestSigningKeyPass;
    }

    public String getResponseDecryptionKeyId() {
        return responseDecryptionKeyId;
    }

    public void setResponseDecryptionKeyId(String responseDecryptionKeyId) {
        this.responseDecryptionKeyId = responseDecryptionKeyId;
    }

    public String getResponseDecryptionKeyPass() {
        return responseDecryptionKeyPass;
    }

    public void setResponseDecryptionKeyPass(String responseDecryptionKeyPass) {
        this.responseDecryptionKeyPass = responseDecryptionKeyPass;
    }

    public String getIdpSSOUrl() {
        return idpSSOUrl;
    }

    public void setIdpSSOUrl(String idpSSOUrl) {
        this.idpSSOUrl = idpSSOUrl;
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

    public String getKeystore() {
        return keystore;
    }

    public String getKeystorePass() {
        return keystorePass;
    }

    public void setKeystore(String keystore) {
        this.keystore = keystore;
    }

    public void setKeystorePass(String keystorePass) {
        this.keystorePass = keystorePass;
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }

    public String getSamlAssertionConsumerUrl() {
        return samlAssertionConsumerUrl;
    }

    public void setSamlAssertionConsumerUrl(String samlAssertionConsumerUrl) {
        this.samlAssertionConsumerUrl = samlAssertionConsumerUrl;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }
}
