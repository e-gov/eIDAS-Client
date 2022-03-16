package ee.ria.eidas.client.session;

import com.google.common.base.Throwables;
import com.google.common.io.ByteSource;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.util.SerializationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.crypto.AesCipherService;
import org.apache.shiro.crypto.CipherService;
import org.apache.shiro.util.Assert;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.keys.AesKey;

import javax.annotation.PreDestroy;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HazelcastRequestSessionServiceImpl implements RequestSessionService {

    public static final String UNANSWERED_REQUESTS_MAP = "unansweredRequestsMap";
    private final HazelcastInstance hazelcastInstance;
    private final CipherExecutor cipherExecutor;
    private int maxAuthenticationLifetime;

    public HazelcastRequestSessionServiceImpl(EidasClientProperties properties, HazelcastInstance hazelcastInstance) {
        log.debug("Using in Hazelcast map for request tracking");
        this.hazelcastInstance = hazelcastInstance;
        this.cipherExecutor = new DefaultCipherExecutor(properties.getHazelcastEncryptionKey(), properties.getHazelcastSigningKey(), properties.getHazelcastEncryptionAlg(), properties.getHazelcastSigningAlgorithm());
        this.maxAuthenticationLifetime = properties.getMaximumAuthenticationLifetime();
    }

    @Override
    public void saveRequestSession(String requestID, RequestSession requestSession) {
        String encodedSessionId = this.encodeSessionId(requestID);
        IMap<String, RequestSession> sessionMap = this.getRequestSessionMapInstance(UNANSWERED_REQUESTS_MAP);
        log.debug("Adding request [{}] with ttl [{}s]", requestSession.getRequestId(), maxAuthenticationLifetime);
        RequestSession encodedSession = sessionMap.putIfAbsent(encodedSessionId, this.encodeRequestSession(requestSession), maxAuthenticationLifetime, TimeUnit.SECONDS);
        if (encodedSession == null) {
            log.debug("Added request [{}] with ttl [{}s]", encodedSessionId, maxAuthenticationLifetime);
        } else {
            throw new EidasClientException("A request with an ID: " + requestID + " already exists!");
        }
    }

    @Override
    public RequestSession getAndRemoveRequestSession(String requestID) {
        Assert.isTrue(StringUtils.isNotBlank(requestID), "requestID cannot be empty!");
        String encodedSessionId = this.encodeSessionId(requestID);
        IMap<String, RequestSession> sessionMap = this.getRequestSessionMapInstance(UNANSWERED_REQUESTS_MAP);
        log.debug("Lookup request [{}] with encoded id [{}] from map [{}]", requestID, encodedSessionId, UNANSWERED_REQUESTS_MAP);
        RequestSession session = sessionMap.remove(encodedSessionId);
        return this.decodeRequestSession(session);
    }

    private RequestSession encodeRequestSession(RequestSession session) {
        Assert.notNull(session, "Session passed is null and cannot be encoded");
        try {
            log.debug("Encoding session [{}]", session);
            byte[] encodedSessionObject = SerializationUtils.serializeAndEncodeObject(this.cipherExecutor, session);
            String encodedSessionId = this.encodeSessionId(session.getRequestId());
            RequestSession encodedSession = new EncodedRequestSession(encodedSessionId, ByteSource.wrap(encodedSessionObject).read());
            log.debug("Created encoded session [{}]", encodedSession);
            return encodedSession;
        } catch (final IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private RequestSession decodeRequestSession(RequestSession session) {
        if (session == null)
            return null;

        try {
            log.debug("Attempting to decode [{}]", session);
            EncodedRequestSession encodedSession = (EncodedRequestSession) session;
            UnencodedRequestSession result = SerializationUtils.decodeAndDeserializeObject(encodedSession.getEncodedRequestSession(), this.cipherExecutor, RequestSession.class);
            log.debug("Decoded session to [{}]", result);
            return result;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private String encodeSessionId(final String sessionId) {
        Assert.isTrue(StringUtils.isNotBlank(sessionId), "sessionID cannot be empty!");
        final String encodedId = sha512(sessionId);
        log.debug("Encoded original session id [{}] to [{}]", sessionId, encodedId);
        return encodedId;
    }

    private IMap<String, RequestSession> getRequestSessionMapInstance(String mapName) {
        try {
            IMap<String, RequestSession> inst = this.hazelcastInstance.getMap(mapName);
            log.debug("Located Hazelcast map instance [{}]", mapName);
            return inst;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to locate map instance: " + mapName ,e);
        }
    }

    public static String sha512(final String data) {
        return digest(MessageDigestAlgorithms.SHA_512, data.getBytes(StandardCharsets.UTF_8));
    }

    public static String digest(final String alg, final byte[] data) {
        return Hex.encodeHexString(rawDigest(alg, data));
    }

    public static byte[] rawDigest(final String alg, final byte[] data) {
        try {
            final MessageDigest digest = MessageDigest.getInstance(alg);
            digest.reset();
            return digest.digest(data);
        } catch (final Exception cause) {
            throw new SecurityException(cause);
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            log.info("Shutting down Hazelcast instance [{}]", this.hazelcastInstance.getConfig().getInstanceName());
            this.hazelcastInstance.shutdown();
        } catch (Exception e) {
            log.debug(e.getMessage());
        }

    }

    public static class DefaultCipherExecutor implements CipherExecutor<byte[], byte[]>{

        private String encryptionAlgorithm = "AES";
        private String encryptionKey;
        private AesKey signingKey;
        private String signingAlgorithm = AlgorithmIdentifiers.HMAC_SHA512;

        public DefaultCipherExecutor(
                final String encryptionSecretKey,
                final String signingSecretKey,
                final String secretKeyAlg,
                final String signingAlgorithm) {

            org.springframework.util.Assert.notNull(encryptionSecretKey, "No encryption key is defined.");
            org.springframework.util.Assert.notNull(signingSecretKey, "Secret key for signing is not defined.");

            this.signingKey = new AesKey(signingSecretKey.getBytes(StandardCharsets.UTF_8));
            this.encryptionKey = encryptionSecretKey;
            this.encryptionAlgorithm = secretKeyAlg;
            this.signingAlgorithm = signingAlgorithm;
        }

        protected byte[] sign(final byte[] value) {
            return signJws(this.signingKey, value, this.signingAlgorithm);
        }

        protected byte[] verifySignature(final byte[] value) {
            return verifyJwsSignature(this.signingKey, value);
        }

        public byte[] encode(final byte[] value) {
            try {
                final Key key = new SecretKeySpec(this.encryptionKey.getBytes(StandardCharsets.UTF_8),
                        this.encryptionAlgorithm);
                final CipherService cipher = new AesCipherService();
                final byte[] result = cipher.encrypt(value, key.getEncoded()).getBytes();
                return sign(result);
            } catch (final Exception e) {
                log.error(e.getMessage(), e);
                throw Throwables.propagate(e);
            }
        }

        public byte[] decode(final byte[] value) {
            try {
                final byte[] verifiedValue = verifySignature(value);
                if (verifiedValue == null)
                    throw new IllegalStateException("Invalid signature detected!");
                final Key key = new SecretKeySpec(this.encryptionKey.getBytes(StandardCharsets.UTF_8),
                        this.encryptionAlgorithm);
                final CipherService cipher = new AesCipherService();
                return cipher.decrypt(verifiedValue, key.getEncoded()).getBytes();
            } catch (final Exception e) {
                throw Throwables.propagate(e);
            }
        }

        public static byte[] signJws(final Key key, final byte[] value, String algorithm) {
            try {
                final String base64 = Base64.getEncoder().encodeToString(value);
                final JsonWebSignature jws = new JsonWebSignature();
                jws.setPayload(base64);
                jws.setAlgorithmHeaderValue(algorithm);
                jws.setKey(key);
                return jws.getCompactSerialization().getBytes(StandardCharsets.UTF_8);
            } catch (final Exception e) {
                throw Throwables.propagate(e);
            }
        }

        public static byte[] verifyJwsSignature(final Key signingKey, final byte[] value) {
            try {
                final String asString = new String(value, StandardCharsets.UTF_8);
                final JsonWebSignature jws = new JsonWebSignature();
                jws.setCompactSerialization(asString);
                jws.setKey(signingKey);

                final boolean verified = jws.verifySignature();
                if (verified) {
                    final String payload = jws.getPayload();
                    log.trace("Successfully decoded value. Result in Base64-encoding is [{}]", payload);
                    return Base64.getDecoder().decode(payload);
                }
                return null;
            } catch (final Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }
}
