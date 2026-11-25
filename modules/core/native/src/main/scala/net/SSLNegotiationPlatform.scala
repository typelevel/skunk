// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import fs2.io.net.tls.TLSParameters

private[net] trait SSLNegotiationPlatform {

  /**
   * Default TLS parameters naming `host`: s2n sends it as SNI and verifies the server certificate
   * against it. Without a server name s2n rejects every certificate.
   */
  def serverNameParameters(host: String): TLSParameters =
    TLSParameters(serverName = Some(host))

}
