package ee.ria.eidas.client.util;

import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.common.SAMLObjectContentReference;
import org.opensaml.saml.common.SignableSAMLObject;
import org.opensaml.security.SecurityException;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.SignatureSigningParameters;
import org.opensaml.xmlsec.algorithm.DigestAlgorithm;
import org.opensaml.xmlsec.algorithm.SignatureAlgorithm;
import org.opensaml.xmlsec.keyinfo.KeyInfoGenerator;
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureSupport;
import org.opensaml.xmlsec.signature.support.Signer;
import org.springframework.util.Assert;


public class SAMLSigner {

    private final String signatureAlgorithmUri;
    private final Credential credential;

    public SAMLSigner(String signatureAlgorithmUri, Credential credential) {
        Assert.notNull(signatureAlgorithmUri, "Signature algorithm must be provided!");
        Assert.notNull(credential, "Signing credentials must be provided!");
        this.signatureAlgorithmUri = signatureAlgorithmUri;
        this.credential = credential;
    }

    public void sign(SignableSAMLObject samlObject) throws SecurityException, MarshallingException, SignatureException {
        Signature signature = buildSignature();
        SignatureAlgorithm signatureAlgorithm = OpenSAMLUtils.getSignatureAlgorithm(signatureAlgorithmUri);
        DigestAlgorithm digestAlgorithm = OpenSAMLUtils.getRelatedDigestAlgorithm(signatureAlgorithmUri);
        SignatureSigningParameters params = getSignatureSigningParameters(credential, signatureAlgorithm, digestAlgorithm);
        SignatureSupport.prepareSignatureParams(signature, params);
        samlObject.setSignature(signature);
        ((SAMLObjectContentReference) signature.getContentReferences().get(0)).setDigestAlgorithm(params.getSignatureReferenceDigestMethod());
        XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(samlObject).marshall(samlObject);
        Signer.signObject(signature);
    }

    private org.opensaml.xmlsec.signature.Signature buildSignature() {
        return (org.opensaml.xmlsec.signature.Signature) XMLObjectProviderRegistrySupport.getBuilderFactory().getBuilder(org.opensaml.xmlsec.signature.Signature.DEFAULT_ELEMENT_NAME).buildObject(org.opensaml.xmlsec.signature.Signature.DEFAULT_ELEMENT_NAME);
    }

    private SignatureSigningParameters getSignatureSigningParameters(Credential credential, SignatureAlgorithm signatureAlgorithm, DigestAlgorithm digestAlgorithm) {
        SignatureSigningParameters params = new SignatureSigningParameters();
        params.setSigningCredential(credential);
        params.setSignatureAlgorithm(signatureAlgorithm.getURI());
        params.setSignatureCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        params.setSignatureReferenceDigestMethod(digestAlgorithm.getURI());
        params.setKeyInfoGenerator(getX509KeyInfoGenerator());
        return params;
    }

    private KeyInfoGenerator getX509KeyInfoGenerator() {
        X509KeyInfoGeneratorFactory x509KeyInfoGenerator = new X509KeyInfoGeneratorFactory();
        x509KeyInfoGenerator.setEmitEntityCertificate(true);
        return x509KeyInfoGenerator.newInstance();
    }
}
