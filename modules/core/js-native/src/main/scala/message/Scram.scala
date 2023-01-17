// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import com.armanbilge.SaslPrep
import scodec.bits.ByteVector
import scodec.codecs.utf8

/**
  * Partial implementation of [RFC5802](https://tools.ietf.org/html/rfc5802), as needed by PostgreSQL.
  * 
  * That is, only features used by PostgreSQL are implemented -- e.g., channel binding is not supported and
  * optional message fields omitted by PostgreSQL are not supported.
  */
private[skunk] object Scram extends ScramPlatform {
  val SaslMechanism = "SCRAM-SHA-256"

  val NoChannelBinding = ByteVector.view("n,,".getBytes)

  private implicit class StringOps(val value: String) extends AnyVal {
    def bytesUtf8: ByteVector = ByteVector.view(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  }

  def clientFirstBareWithNonce(nonce: String): ByteVector =
    s"n=,r=${nonce}".bytesUtf8

  case class ServerFirst(nonce: String, salt: ByteVector, iterations: Int)
  object ServerFirst {
    private val Pattern = """r=([\x21-\x2B\x2D-\x7E]+),s=([A-Za-z0-9+/]+={0,2}),i=(\d+)""".r

    def decode(bytes: ByteVector): Option[ServerFirst] =
      utf8.decodeValue(bytes.bits).toOption.flatMap {
        case Pattern(r, s, i) =>
          Some(ServerFirst(r, ByteVector.fromValidBase64(s), i.toInt))
        case _ => 
          None
      }
  }

  case class ClientProof(value: String)

  case class ClientFinalWithoutProof(channelBinding: String, nonce: String) {
    override def toString: String = s"c=$channelBinding,r=$nonce"
    def encode: ByteVector = toString.bytesUtf8
    def encodeWithProof(proof: ClientProof): ByteVector = (toString ++ s",p=${proof.value}").bytesUtf8
  }

  case class Verifier(value: ByteVector)

  case class ServerFinal(verifier: Verifier)
  object ServerFinal {
    private val Pattern = """v=([A-Za-z0-9+/]+={0,2})""".r
    def decode(bytes: ByteVector): Option[ServerFinal] =
      utf8.decodeValue(bytes.bits).toOption.flatMap {
        case Pattern(v) =>
          Some(ServerFinal(Verifier(ByteVector.fromValidBase64(v))))
        case _ => 
          None
      }
  }

  private def makeClientProofAndServerSignature(password: String, salt: ByteVector, iterations: Int, clientFirstMessageBare: ByteVector, serverFirstMessage: ByteVector, clientFinalMessageWithoutProof: ByteVector): (ClientProof, Verifier) = {
    val saltedPassword = Hi(SaslPrep.saslPrepStored(password), salt, iterations)
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

  def saslInitialResponse(channelBinding: ByteVector, clientFirstBare: ByteVector): SASLInitialResponse =
    SASLInitialResponse(SaslMechanism, channelBinding ++ clientFirstBare)

  def saslChallenge(
      password: String, 
      channelBinding: ByteVector, 
      serverFirst: ServerFirst, 
      clientFirstBare: ByteVector, 
      serverFirstBytes: ByteVector
  ): (SASLResponse, Verifier) = {
    val clientFinalMessageWithoutProof = ClientFinalWithoutProof(channelBinding.toBase64, serverFirst.nonce)
    val (clientProof, expectedVerifier) = 
      makeClientProofAndServerSignature(
          password, 
          serverFirst.salt, 
          serverFirst.iterations, 
          clientFirstBare, 
          serverFirstBytes, 
          clientFinalMessageWithoutProof.encode)
    (SASLResponse(clientFinalMessageWithoutProof.encodeWithProof(clientProof)), expectedVerifier)
  }
}
