// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import fs2.io.net.tls.TLSParameters
import javax.net.ssl.SNIHostName

private[net] trait SSLNegotiationPlatform {

  /** Default TLS parameters that send `host` as the SNI server name. */
  def serverNameParameters(host: String): TLSParameters =
    TLSParameters(serverNames = Some(List(new SNIHostName(host))))

}
