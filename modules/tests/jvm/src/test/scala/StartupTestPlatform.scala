// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

trait StartupTestPlatform {
  
  type ConnectException = java.net.ConnectException
  type UnknownHostException = java.net.UnknownHostException

}
