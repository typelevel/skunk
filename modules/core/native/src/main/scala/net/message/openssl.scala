// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scala.scalanative.unsafe._

@link("crypto")
@extern
private[message] object openssl {

  final val EVP_MAX_MD_SIZE = 64

  type EVP_MD
  type EVP_MD_CTX
  type ENGINE

  def EVP_get_digestbyname(name: Ptr[CChar]): Ptr[EVP_MD] = extern

  def EVP_MD_CTX_new(): Ptr[EVP_MD_CTX] = extern
  def EVP_MD_CTX_reset(ctx: Ptr[EVP_MD_CTX]): CInt = extern
  def EVP_MD_CTX_free(ctx: Ptr[EVP_MD_CTX]): Unit = extern

  def EVP_DigestInit_ex(ctx: Ptr[EVP_MD_CTX], `type`: Ptr[EVP_MD], impl: Ptr[ENGINE]): CInt = extern
  def EVP_DigestUpdate(ctx: Ptr[EVP_MD_CTX], d: Ptr[Byte], cnt: CSize): CInt = extern
  def EVP_DigestFinal_ex(ctx: Ptr[EVP_MD_CTX], md: Ptr[Byte], s: Ptr[CUnsignedInt]): CInt = extern
  def EVP_Digest(
      data: Ptr[Byte],
      count: CSize,
      md: Ptr[Byte],
      size: Ptr[CUnsignedInt],
      `type`: Ptr[EVP_MD],
      impl: Ptr[ENGINE]
  ): CInt = extern

  def HMAC(
      evp_md: Ptr[EVP_MD],
      key: Ptr[Byte],
      key_len: Int,
      d: Ptr[Byte],
      n: CSize,
      md: Ptr[Byte],
      md_len: Ptr[CUnsignedInt]
  ): Ptr[CUnsignedChar] = extern

  def PKCS5_PBKDF2_HMAC(
      pass: Ptr[CChar],
      passlen: CInt,
      salt: Ptr[Byte],
      saltlen: CInt,
      iter: CInt,
      digest: Ptr[EVP_MD],
      keylen: CInt,
      out: Ptr[Byte]
  ): CInt = extern

  def RAND_bytes(
      buf: Ptr[CChar],
      num: CInt
  ): CInt = extern

}
