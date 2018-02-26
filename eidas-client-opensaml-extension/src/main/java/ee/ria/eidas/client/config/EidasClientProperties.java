package ee.ria.eidas.client.config;

import javax.validation.constraints.NotNull;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.SPType;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "eidas.client")
public class EidasClientProperties {

    private static final int DEFAULT_MAXIMUM_AUTHENTICTION_LIFETIME = 3600;
    private static final String DEFAULT_NAMEID_POLICY_FORMAT = "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified";
    public static final String SESSION_ATTRIBUTE_USER_AUTHENTICATED = "authenticated";
    public static final String SESSION_ATTRIBUTE_ORIGINALLY_REQUESTED_URL = "originally_requested_url";
    public static final String DEFAULT_IDP_METADATA_SIGN_CERT_KEY = "metadata";

    @NotNull
    private String keystore;

    @NotNull
    private String keystorePass;

    @NotNull
    private String metadataSigningKeyId;

    @NotNull
    private String metadataSigningKeyPass;

    @NotNull
    private String metadataSignatureAlgorithm = SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256;

    @NotNull
    private List<String> metadataExtensionsDigestmethods = Arrays.asList(SignatureConstants.ALGO_ID_DIGEST_SHA512);

    @NotNull
    private Integer metadataValidityInDays = 1;

    @NotNull
    private String requestSigningKeyId;

    @NotNull
    private String requestSigningKeyPass;

    @NotNull
    private String responseDecryptionKeyId;

    @NotNull
    private String responseDecryptionKeyPass;

    @NotNull
    private String callbackUrl;

    @NotNull
    private String idpMetadataUrl;

    @NotNull
    private String spEntityId;

    @NotNull
    private String providerName;

    @NotNull
    private List<String> availableCountries;

    @NotNull
    private SPType spType = SPType.PUBLIC;

    @NotNull
    private AssuranceLevel defaultLoa = AssuranceLevel.SUBSTANTIAL;

    @NotNull
    private String requestSignatureAlgorithm = SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256;

    private int maximumAuthenticationLifetime = DEFAULT_MAXIMUM_AUTHENTICTION_LIFETIME;

    private String nameIdPolicyFormat = DEFAULT_NAMEID_POLICY_FORMAT;

    private String idpMetadataSigningCertificateKeyId = DEFAULT_IDP_METADATA_SIGN_CERT_KEY;

    private String samlAssertionConsumerUrl = "/returnUrl";

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

    public String getMetadataSignatureAlgorithm() {
        return metadataSignatureAlgorithm;
    }

    public void setMetadataSignatureAlgorithm(String metadataSignatureAlgorithm) {
        this.metadataSignatureAlgorithm = metadataSignatureAlgorithm;
    }

    public List<String> getMetadataExtensionsDigestmethods() {
        return metadataExtensionsDigestmethods;
    }

    public void setMetadataExtensionsDigestmethods(List<String> metadataExtensionsDigestmethods) {
        this.metadataExtensionsDigestmethods = metadataExtensionsDigestmethods;
    }

    public Integer getMetadataValidityInDays() {
        return metadataValidityInDays;
    }

    public void setMetadataValidityInDays(Integer metadataValidityInDays) {
        this.metadataValidityInDays = metadataValidityInDays;
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

    public SPType getSpType() {
        return spType;
    }

    public void setSpType(SPType spType) {
        this.spType = spType;
    }

    public String getIdpMetadataSigningCertificateKeyId() {
        return idpMetadataSigningCertificateKeyId;
    }

    public void setIdpMetadataSigningCertificateKeyId(String idpMetadataSigningCertificateKeyId) {
        this.idpMetadataSigningCertificateKeyId = idpMetadataSigningCertificateKeyId;
    }

    public List<String> getAvailableCountries() {
        return availableCountries;
    }

    public void setAvailableCountries(List<String> availableCountries) {
        this.availableCountries = availableCountries;
    }

    public AssuranceLevel getDefaultLoa() {
        return defaultLoa;
    }

    public void setDefaultLoa(AssuranceLevel defaultLoa) {
        this.defaultLoa = defaultLoa;
    }

    public String getRequestSignatureAlgorithm() {
        return requestSignatureAlgorithm;
    }

    public void setRequestSignatureAlgorithm(String requestSignatureAlgorithm) {
        this.requestSignatureAlgorithm = requestSignatureAlgorithm;
    }
}
