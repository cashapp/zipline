// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.subtle;

import java.security.GeneralSecurityException;
import okio.ByteString;

/**
 * Ed25519 signing.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * Ed25519Sign.KeyPair keyPair = Ed25519Sign.KeyPair.newKeyPair();
 * // securely store keyPair and share keyPair.getPublicKey()
 * Ed25519Sign signer = new Ed25519Sign(keyPair.getPrivateKey());
 * byte[] signature = signer.sign(message);
 * }</pre>
 *
 * @since 1.1.0
 */
public final class Ed25519Sign {
  public static final int SECRET_KEY_LEN = Field25519.FIELD_LEN;

  private final ByteString hashedPrivateKey;
  private final ByteString publicKey;

  /**
   * Constructs a Ed25519Sign with the {@code privateKey}.
   *
   * @param privateKey 32-byte random sequence.
   * @throws GeneralSecurityException if there is no SHA-512 algorithm defined in {@code
   *     EngineFactory}.MESSAGE_DIGEST.
   */
  public Ed25519Sign(final ByteString privateKey) throws GeneralSecurityException {
    if (privateKey.size() != SECRET_KEY_LEN) {
      throw new IllegalArgumentException(
          String.format("Given private key's length is not %s", SECRET_KEY_LEN));
    }

    this.hashedPrivateKey = Ed25519.getHashedScalar(privateKey);
    this.publicKey = Ed25519.scalarMultWithBaseToBytes(this.hashedPrivateKey);
  }

  public ByteString sign(final ByteString data) throws GeneralSecurityException {
    return Ed25519.sign(data, publicKey, hashedPrivateKey);
  }

  /** Defines the KeyPair consisting of a private key and its corresponding public key. */
  public static final class KeyPair {

    private final ByteString publicKey;
    private final ByteString privateKey;

    private KeyPair(ByteString publicKey, ByteString privateKey) {
      this.publicKey = publicKey;
      this.privateKey = privateKey;
    }

    public ByteString getPublicKey() {
      return publicKey;
    }

    public ByteString getPrivateKey() {
      return privateKey;
    }

    /** Returns a new <publicKey, privateKey> KeyPair. */
    public static KeyPair newKeyPair() throws GeneralSecurityException {
      return newKeyPairFromSeed(Random.randBytes(Field25519.FIELD_LEN));
    }

    /** Returns a new <publicKey, privateKey> KeyPair generated from a seed. */
    public static KeyPair newKeyPairFromSeed(ByteString secretSeed) throws GeneralSecurityException {
      if (secretSeed.size() != Field25519.FIELD_LEN) {
        throw new IllegalArgumentException(
            String.format("Given secret seed length is not %s", Field25519.FIELD_LEN));
      }
      ByteString privateKey = secretSeed;
      ByteString publicKey = Ed25519.scalarMultWithBaseToBytes(Ed25519.getHashedScalar(privateKey));
      return new KeyPair(publicKey, privateKey);
    }
  }
}
