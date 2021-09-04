// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.bits.ByteVector
import scodec.codecs.utf8

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

/**
  * Partial implementation of [RFC5802](https://tools.ietf.org/html/rfc5802), as needed by PostgreSQL.
  * 
  * That is, only features used by PostgreSQL are implemented -- e.g., channel binding is not supported and
  * optional message fields omitted by PostgreSQL are not supported.
  */
private[skunk] object Scram {
  val SaslMechanism = "SCRAM-SHA-256"

  val NoChannelBinding = ByteVector.view("n,,".getBytes)

  private implicit class StringOps(val value: String) extends AnyVal {
    def bytesUtf8: ByteVector = ByteVector.view(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  }

  private val normalize = js.Dynamic.global.require("saslprep").asInstanceOf[js.Function1[String, String]]

  def clientFirstBareWithRandomNonce: ByteVector = {
    val nonce = ByteVector.view(crypto.randomBytes(32).asInstanceOf[Uint8Array]).toBase64
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

  private val crypto = js.Dynamic.global.require("crypto")

  private def HMAC(key: ByteVector, str: ByteVector): ByteVector = {
    val mac = crypto.createHmac("sha256", key.toUint8Array)
    mac.update(str.toUint8Array)
    ByteVector.view(mac.digest().asInstanceOf[Uint8Array])
  }

  private def H(input: ByteVector): ByteVector = {
    val hash = crypto.createHash("sha256")
    hash.update(input.toUint8Array)
    ByteVector.view(hash.digest().asInstanceOf[Uint8Array])
  }

  private def Hi(str: String, salt: ByteVector, iterations: Int): ByteVector = {
    // TODO It is unfortunate that we have to use a sync API here when an async is available
    // To make the change here will require running an F[_]: Async up the hiearchy    
    val salted = crypto.pbkdf2Sync(str, salt.toUint8Array, iterations, 8 * 32, "sha256")
    ByteVector.view(salted.asInstanceOf[Uint8Array]).take(32)
  }

  private def makeClientProofAndServerSignature(password: String, salt: ByteVector, iterations: Int, clientFirstMessageBare: ByteVector, serverFirstMessage: ByteVector, clientFinalMessageWithoutProof: ByteVector): (ClientProof, Verifier) = {
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
