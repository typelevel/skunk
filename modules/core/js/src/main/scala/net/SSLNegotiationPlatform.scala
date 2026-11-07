// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import fs2.io.net.tls.TLSParameters

private[net] trait SSLNegotiationPlatform {

  /** Default TLS parameters naming `host`: Node.js sends it as SNI and checks the certificate against it. */
  def serverNameParameters(host: String): TLSParameters =
    TLSParameters(servername = Some(host))

}
