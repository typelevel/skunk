// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

/*
 * Copyright 2008-2018 MongoDB, Inc.
 * Copyright 2017 Tom Bentley
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package skunk.util

import scala.scalajs.js

/** https://github.com/mongodb/mongosql-auth-java/blob/master/src/main/java/org/mongodb/mongosql/auth/plugin/SaslPrep.java
  */
private[skunk] object SaslPrep {

  /** Return the {@code SASLPrep}-canonicalised version of the given {@code str}
    * for use as a query string. This implements the {@code SASLPrep} algorithm
    * defined in <a href="https://tools.ietf.org/html/rfc4013">RFC 4013</a>.
    *
    * @param str
    *   The string to canonicalise.
    * @return
    *   The canonicalised string.
    * @throws IllegalArgumentException
    *   if the string contained prohibited codepoints, or broke the requirements
    *   for bidirectional character handling.
    * @see
    *   <a href="https://tools.ietf.org/html/rfc3454#section-7">RFC 3454,
    *   Section 7</a> for discussion of what a query string is.
    */
  def saslPrepQuery(str: String): String = {
    saslPrep(str, true)
  }

  /** Return the {@code SASLPrep}-canonicalised version of the given {@code str}
    * for use as a stored string. This implements the {@code SASLPrep} algorithm
    * defined in <a href="https://tools.ietf.org/html/rfc4013">RFC 4013</a>.
    *
    * @param str
    *   The string to canonicalise.
    * @return
    *   The canonicalised string.
    * @throws IllegalArgumentException
    *   if the string contained prohibited codepoints, or broke the requirements
    *   for bidirectional character handling.
    * @see
    *   <a href="https://tools.ietf.org/html/rfc3454#section-7">RFC 3454,
    *   Section 7</a> for discussion of what a stored string is.
    */
  def saslPrepStored(str: String): String = {
    saslPrep(str, false)
  }

  private[this] def saslPrep(str: String, allowUnassigned: Boolean) = {
    val chars = str.toCharArray()

    // 1. Map

    // non-ASCII space chars mapped to space
    for (i <- str.indices) {
      val ch = str.charAt(i)
      if (nonAsciiSpace(ch)) {
        chars(i) = ' '
      }
    }

    var length = 0
    for (i <- str.indices) {
      val ch = chars(i)
      if (!mappedToNothing(ch)) {
        chars(length) = ch
        length += 1
      }
    }

    // 2. Normalize
    val normalized = new String(chars, 0, length)
      .asInstanceOf[js.Dynamic]
      .normalize("NKFC")
      .asInstanceOf[String]

    var containsRandALCat = false
    var containsLCat = false
    var initialRandALCat = false
    var i = 0
    while (i < normalized.length()) {
      val codepoint = normalized.codePointAt(i)
      // 3. Prohibit
      if (prohibited(codepoint)) {
        throw new IllegalArgumentException(
          "Prohibited character at position " + i
        )
      }

      // 4. Check bidi
      val directionality = Character.getDirectionality(codepoint)
      val isRandALcat =
        directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
      containsRandALCat |= isRandALcat
      containsLCat |= directionality == Character.DIRECTIONALITY_LEFT_TO_RIGHT

      initialRandALCat |= i == 0 && isRandALcat
      if (!allowUnassigned && !Character.isDefined(codepoint)) {
        throw new IllegalArgumentException(
          "Character at position " + i + " is unassigned"
        )
      }
      i += Character.charCount(codepoint)

      if (initialRandALCat && i >= normalized.length() && !isRandALcat) {
        throw new IllegalArgumentException(
          "First character is RandALCat, but last character is not"
        )
      }

      i += 1
    }
    if (containsRandALCat && containsLCat) {
      throw new IllegalArgumentException(
        "Contains both RandALCat characters and LCat characters"
      )
    }

    normalized
  }

  /** Return true if the given {@code codepoint} is a prohibited character as
    * defined by <a href="https://tools.ietf.org/html/rfc4013#section-2.3">RFC
    * 4013, Section 2.3</a>.
    */
  private[this] def prohibited(codepoint: Int) = {
    nonAsciiSpace(codepoint.toChar) || asciiControl(
      codepoint.toChar
    ) || nonAsciiControl(codepoint) || privateUse(
      codepoint
    ) || nonCharacterCodePoint(codepoint) || surrogateCodePoint(
      codepoint
    ) || inappropriateForPlainText(codepoint) || inappropriateForCanonical(
      codepoint
    ) || changeDisplayProperties(codepoint) || tagging(codepoint)
  }

  /** Return true if the given {@code codepoint} is a tagging character as
    * defined by <a href="https://tools.ietf.org/html/rfc3454#appendix-C.9">RFC
    * 3454, Appendix C.9</a>.
    */
  private def tagging(codepoint: Int) = {
    codepoint == 0xe0001 || 0xe0020 <= codepoint && codepoint <= 0xe007f
  }

  /** Return true if the given {@code codepoint} is change display properties or
    * deprecated characters as defined by <a
    * href="https://tools.ietf.org/html/rfc3454#appendix-C.8">RFC 3454, Appendix
    * C.8</a>.
    */
  private[this] def changeDisplayProperties(codepoint: Int) = {
    codepoint == 0x0340 || codepoint == 0x0341 || codepoint == 0x200e || codepoint == 0x200f || codepoint == 0x202a || codepoint == 0x202b || codepoint == 0x202c || codepoint == 0x202d || codepoint == 0x202e || codepoint == 0x206a || codepoint == 0x206b || codepoint == 0x206c || codepoint == 0x206d || codepoint == 0x206e || codepoint == 0x206f
  }

  /** Return true if the given {@code codepoint} is inappropriate for canonical
    * representation characters as defined by <a
    * href="https://tools.ietf.org/html/rfc3454#appendix-C.7">RFC 3454, Appendix
    * C.7</a>.
    */
  private[this] def inappropriateForCanonical(codepoint: Int) = {
    0x2ff0 <= codepoint && codepoint <= 0x2ffb
  }

  /** Return true if the given {@code codepoint} is inappropriate for plain text
    * characters as defined by <a
    * href="https://tools.ietf.org/html/rfc3454#appendix-C.6">RFC 3454, Appendix
    * C.6</a>.
    */
  private[this] def inappropriateForPlainText(codepoint: Int) = {
    codepoint == 0xfff9 || codepoint == 0xfffa || codepoint == 0xfffb || codepoint == 0xfffc || codepoint == 0xfffd
  }

  /** Return true if the given {@code codepoint} is a surrogate code point as
    * defined by <a href="https://tools.ietf.org/html/rfc3454#appendix-C.5">RFC
    * 3454, Appendix C.5</a>.
    */
  private[this] def surrogateCodePoint(codepoint: Int) = {
    0xd800 <= codepoint && codepoint <= 0xdfff
  }

  /** Return true if the given {@code codepoint} is a non-character code point
    * as defined by <a
    * href="https://tools.ietf.org/html/rfc3454#appendix-C.4">RFC 3454, Appendix
    * C.4</a>.
    */
  private[this] def nonCharacterCodePoint(codepoint: Int) = {
    0xfdd0 <= codepoint && codepoint <= 0xfdef || 0xfffe <= codepoint && codepoint <= 0xffff || 0x1fffe <= codepoint && codepoint <= 0x1ffff || 0x2fffe <= codepoint && codepoint <= 0x2ffff || 0x3fffe <= codepoint && codepoint <= 0x3ffff || 0x4fffe <= codepoint && codepoint <= 0x4ffff || 0x5fffe <= codepoint && codepoint <= 0x5ffff || 0x6fffe <= codepoint && codepoint <= 0x6ffff || 0x7fffe <= codepoint && codepoint <= 0x7ffff || 0x8fffe <= codepoint && codepoint <= 0x8ffff || 0x9fffe <= codepoint && codepoint <= 0x9ffff || 0xafffe <= codepoint && codepoint <= 0xaffff || 0xbfffe <= codepoint && codepoint <= 0xbffff || 0xcfffe <= codepoint && codepoint <= 0xcffff || 0xdfffe <= codepoint && codepoint <= 0xdffff || 0xefffe <= codepoint && codepoint <= 0xeffff || 0xffffe <= codepoint && codepoint <= 0xfffff || 0x10fffe <= codepoint && codepoint <= 0x10ffff
  }

  /** Return true if the given {@code codepoint} is a private use character as
    * defined by <a href="https://tools.ietf.org/html/rfc3454#appendix-C.3">RFC
    * 3454, Appendix C.3</a>.
    */
  private[this] def privateUse(codepoint: Int) = {
    0xe000 <= codepoint && codepoint <= 0xf8ff || 0xf000 <= codepoint && codepoint <= 0xffffd || 0x100000 <= codepoint && codepoint <= 0x10fffd
  }

  /** Return true if the given {@code ch} is a non-ASCII control character as
    * defined by <a
    * href="https://tools.ietf.org/html/rfc3454#appendix-C.2.2">RFC 3454,
    * Appendix C.2.2</a>.
    */
  private[this] def nonAsciiControl(codepoint: Int) = {
    0x0080 <= codepoint && codepoint <= 0x009f || codepoint == 0x06dd || codepoint == 0x070f || codepoint == 0x180e || codepoint == 0x200c || codepoint == 0x200d || codepoint == 0x2028 || codepoint == 0x2029 || codepoint == 0x2060 || codepoint == 0x2061 || codepoint == 0x2062 || codepoint == 0x2063 || 0x206a <= codepoint && codepoint <= 0x206f || codepoint == 0xfeff || 0xfff9 <= codepoint && codepoint <= 0xfffc || 0x1d173 <= codepoint && codepoint <= 0x1d17a
  }

  /** Return true if the given {@code ch} is an ASCII control character as
    * defined by <a
    * href="https://tools.ietf.org/html/rfc3454#appendix-C.2.1">RFC 3454,
    * Appendix C.2.1</a>.
    */
  private[this] def asciiControl(ch: Char) = {
    ch <= '\u001F' || ch == '\u007F'
  }

  /** Return true if the given {@code ch} is a non-ASCII space character as
    * defined by <a
    * href="https://tools.ietf.org/html/rfc3454#appendix-C.1.2">RFC 3454,
    * Appendix C.1.2</a>.
    */
  private[this] def nonAsciiSpace(ch: Char) = {
    ch == '\u00A0' || ch == '\u1680' || '\u2000' <= ch && ch <= '\u200B' || ch == '\u202F' || ch == '\u205F' || ch == '\u3000'
  }

  /** Return true if the given {@code ch} is a "commonly mapped to nothing"
    * character as defined by <a
    * href="https://tools.ietf.org/html/rfc3454#appendix-B.1">RFC 3454, Appendix
    * B.1</a>.
    */
  private[this] def mappedToNothing(ch: Char) = {
    ch == '\u00AD' || ch == '\u034F' || ch == '\u1806' || ch == '\u180B' || ch == '\u180C' || ch == '\u180D' || ch == '\u200B' || ch == '\u200C' || ch == '\u200D' || ch == '\u2060' || '\uFE00' <= ch && ch <= '\uFE0F' || ch == '\uFEFF'
  }

}
