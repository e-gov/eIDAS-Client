package ee.ria.eidas.client.config;

import ee.ria.eidas.client.authnrequest.AssuranceLevel;
import ee.ria.eidas.client.authnrequest.EidasAttribute;
import ee.ria.eidas.client.authnrequest.SPType;
import lombok.Data;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Nonnegative;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.Arrays;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "eidas.client")
@Data
public class EidasClientProperties {

    private static final int DEFAULT_MAXIMUM_AUTHENTICTION_LIFETIME = 900;
    private static final String DEFAULT_IDP_METADATA_SIGN_CERT_KEY = "metadata";
    private static final int DEFAULT_ACCEPTED_CLOCK_SKEW = 2;
    private static final int DEFAULT_RESPONSE_MESSAGE_LIFETIME = 900;
    private static final List<EidasAttribute> DEFAULT_ALLOWED_EIDAS_ATTRIBUTES = Arrays.asList(EidasAttribute.values());

    public static final String DEFAULT_HAZELCAST_SIGNING_ALGORITHM = "HS512";
    public static final String DEFAULT_HAZELCAST_ENCRYPTION_ALGORITHM = "AES";

    @NotNull
    private String keystore;

    @NotNull
    private String keystorePass;

    @NotNull
    private String metadataSigningKeyId;

    @NotNull
    private String metadataSigningKeyPass;

    @NotNull
    private String metadataSignatureAlgorithm = SignatureConstants.ALGO_ID_SIGNATURE_ECDSA_SHA512;

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
    @ValidIsoCountryCodes
    private List<String> availableCountries;

    @NotNull
    private SPType spType = SPType.PUBLIC;

    @NotNull
    private AssuranceLevel defaultLoa = AssuranceLevel.SUBSTANTIAL;

    @NotNull
    private String requestSignatureAlgorithm = SignatureConstants.ALGO_ID_SIGNATURE_ECDSA_SHA512;

    @NotNull
    @Nonnegative
    private int maximumAuthenticationLifetime = DEFAULT_MAXIMUM_AUTHENTICTION_LIFETIME;

    @NotNull
    private String idpMetadataSigningCertificateKeyId = DEFAULT_IDP_METADATA_SIGN_CERT_KEY;

    @NotNull
    @Nonnegative
    private int acceptedClockSkew = DEFAULT_ACCEPTED_CLOCK_SKEW;

    @NotNull
    private int responseMessageLifetime = DEFAULT_RESPONSE_MESSAGE_LIFETIME;

    private boolean hazelcastEnabled = false;

    private String hazelcastConfig;

    private String hazelcastEncryptionKey;

    private String hazelcastEncryptionAlg = DEFAULT_HAZELCAST_ENCRYPTION_ALGORITHM;

    private String hazelcastSigningKey;

    private int hazelcastMaxHeapSizePercentage = 50;

    private String hazelcastEvictionPolicy = "LRU";

    private int hazelcastStorageTimeout = 0;

    @Pattern(regexp="^(HS512|HS384|HS256)$",message="Invalid signing algorithm! Must be one of the following values: HS512, HS384, HS256.")
    private String hazelcastSigningAlgorithm = DEFAULT_HAZELCAST_SIGNING_ALGORITHM;

    @NotNull
    private List<EidasAttribute> allowedEidasAttributes = DEFAULT_ALLOWED_EIDAS_ATTRIBUTES;


}
