package net.pkhapps.playground.microservices.directory.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.security.*;
import java.util.Base64;
import java.util.Objects;

/**
 * Base class for resource instance registrations. The registration contains a digital signature that must be verified
 * by the service directory using the corresponding resource's public key.
 *
 * @param <ID> the resource ID type.
 */
public abstract class ResourceInstanceRegistration<ID> extends ResourceInstanceDescriptor<ID> {

    private static final String DEFAULT_SIGNATURE_ALGORITHM = "SHA256withRSA";

    @JsonProperty
    private final String algorithm;
    @JsonProperty
    private final String signature;

    /**
     * Creates a new resource instance registration.
     *
     * @param descriptor the resource instance descriptor.
     * @param privateKey the private key to use when creating the digital signature. The private key will not be stored
     *                   anywhere.
     */
    public ResourceInstanceRegistration(ResourceInstanceDescriptor<ID> descriptor, PrivateKey privateKey) {
        super(descriptor);
        this.algorithm = DEFAULT_SIGNATURE_ALGORITHM;

        try {
            var signature = Signature.getInstance(this.algorithm);
            signature.initSign(Objects.requireNonNull(privateKey, "privateKey must not be null"));
            updateSignature(signature);
            this.signature = Base64.getEncoder().encodeToString(signature.sign());
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException ex) {
            throw new IllegalStateException("Could not create digital signature", ex);
        }
    }

    /**
     * Constructor used by Jackson and unit tests only. Clients should not use directly.
     */
    @JsonCreator
    protected ResourceInstanceRegistration(@JsonProperty(value = "id", required = true) ID id,
                                           @JsonProperty(value = "version", required = true) Version version,
                                           @JsonProperty(value = "clientUri", required = true) URI clientUri,
                                           @JsonProperty(value = "pingUri", required = true) URI pingUri,
                                           @JsonProperty(value = "algorithm", required = true) String algorithm,
                                           @JsonProperty(value = "signature", required = true) String signature) {
        super(id, version, clientUri, pingUri);
        this.algorithm = algorithm;
        this.signature = signature;
    }

    /**
     * Returns the algorithm used to create the digital signature.
     *
     * @see #getSignature()
     */
    public final String getAlgorithm() {
        return algorithm;
    }

    /**
     * Returns the digital signature of this instance registration object as a Base64 encoded string.
     *
     * @see #verifySignature(PublicKey)
     */
    public final String getSignature() {
        return signature;
    }

    /**
     * Checks whether the provided {@link #getSignature() signature} is valid. The signature is valid if the
     * value of this registration has not changed and the public key corresponds to the private key that was used
     * to create the signature in the first place.
     *
     * @param publicKey the public key to use when verifying the signature.
     * @return true if the signature is valid, false if it is not.
     */
    public final boolean verifySignature(PublicKey publicKey) {
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        try {
            var signature = Signature.getInstance(algorithm);
            signature.initVerify(publicKey);
            updateSignature(signature);
            return signature.verify(Base64.getDecoder().decode(this.signature));
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException ex) {
            throw new IllegalStateException("Could not verify signature", ex);
        }
    }

    private void updateSignature(Signature signature) throws SignatureException {
        signature.update(getId().toString().getBytes());
        signature.update(getVersion().toString().getBytes());
        signature.update(getClientUri().toString().getBytes());
        signature.update(getPingUri().toString().getBytes());
        signature.update(algorithm.getBytes());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ResourceInstanceRegistration<?> that = (ResourceInstanceRegistration<?>) o;
        return Objects.equals(algorithm, that.algorithm) &&
                Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), algorithm, signature);
    }
}