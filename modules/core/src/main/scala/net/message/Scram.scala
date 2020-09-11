// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.bits.ByteVector
import scodec.codecs.utf8

/**
  * Implementation of [RFC5802](https://tools.ietf.org/html/rfc5802) as needed by PostgreSQL.
  * 
  * That is, only features used by PostgreSQL are implemented -- e.g., channel binding is not supported and
  * optional message fields that are omitted by PostgreSQL are not supported.
  */
private[skunk] object Scram {
  private implicit class StringOps(val value: String) extends AnyVal {
    def bytesUtf8: ByteVector = ByteVector.view(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  }

  private def normalize(value: String): String =
    com.ongres.saslprep.SaslPrep.saslPrep(value, false)

  def clientFirstBare: ByteVector = {
    val nonce = {
      val arr = new Array[Byte](32)
      java.security.SecureRandom.getInstanceStrong().nextBytes(arr)
      ByteVector.view(arr).toBase64
    }
    clientFirstBareWithNonce(nonce)
  }

  def clientFirstBareWithNonce(nonce: String): ByteVector =
    s"n=,r=${nonce}".bytesUtf8

  case class ServerFirst(nonce: String, salt: ByteVector, iterations: Int)
  object ServerFirst {
    private val Pattern = """r=([\x21-\x2B\x2D-\x7E]+),s=([A-Za-z0-9+/]+={0,2}),i=(\d+)""".r

    def decode(bytes: ByteVector): Option[ServerFirst] =
      utf8.decodeValue(bytes.bits).toOption.flatMap {
        case Pattern(r, s, i) =>
          Some(ServerFirst(r, ByteVector.fromBase64(s).get, i.toInt))
        case _ => 
          None
      }
  }

  case class ClientProof(value: String) {
    override def toString: String = value
  }

  case class ClientFinalWithoutProof(channelBinding: String, nonce: String) {
    override def toString: String = s"c=$channelBinding,r=$nonce"
    def encode: ByteVector = toString.bytesUtf8
    def encodeWithProof(proof: ClientProof): ByteVector = (toString ++ s",p=$proof").bytesUtf8
  }

  case class Verifier(value: ByteVector)

  case class ServerFinal(verifier: Verifier)
  object ServerFinal {
    private val Pattern = """v=([A-Za-z0-9+/]+={0,2})""".r
    def decode(bytes: ByteVector): Option[ServerFinal] =
      utf8.decodeValue(bytes.bits).toOption.flatMap {
        case Pattern(v) =>
          Some(ServerFinal(Verifier(ByteVector.fromBase64(v).get)))
        case _ => 
          None
      }
  }

  private def HMAC(key: ByteVector, str: ByteVector): ByteVector = {
    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(new javax.crypto.spec.SecretKeySpec(key.toArray, "HmacSHA256"))
    ByteVector.view(mac.doFinal(str.toArray))
  }

  private def H(input: ByteVector): ByteVector = input.digest("SHA-256")

  private def Hi(str: String, salt: ByteVector, iterations: Int): ByteVector = {
    val spec = new javax.crypto.spec.PBEKeySpec(str.toCharArray, salt.toArray, iterations, 8 * 32)
    val salted = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded
    ByteVector.view(salted)
  }

  def makeClientProofAndServerSignature(password: String, salt: ByteVector, iterations: Int, clientFirstMessageBare: ByteVector, serverFirstMessage: ByteVector, clientFinalMessageWithoutProof: ByteVector): (ClientProof, Verifier) = {
    val saltedPassword = Hi(normalize(password), salt, iterations)
    val clientKey = HMAC(saltedPassword, "Client Key".bytesUtf8)
    val storedKey = H(clientKey)
    val comma = ",".bytesUtf8
    val authMessage = clientFirstMessageBare ++ comma ++ serverFirstMessage ++ comma ++ clientFinalMessageWithoutProof
    val clientSignature = HMAC(storedKey, authMessage)
    val proof = clientKey xor clientSignature
    val serverKey = HMAC(saltedPassword, "Server Key".bytesUtf8)
    val serverSignature = HMAC(serverKey, authMessage)
    (ClientProof(proof.toBase64), Verifier(serverSignature))
  }
}