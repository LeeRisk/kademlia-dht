package com.tommo.kademlia.protocol

case class Host(hostname: String, port: Int) {
  import Host._

  val (startPort, endPort) = PortRange
  require(port >= startPort && port <= endPort, s"Port range must be in the range $startPort and $endPort inclusive")

}

object Host {
  def apply(host: String) = {
    host.split(":") match {
      case Array(hostname, port) => new Host(hostname, port.toInt)
      case _ => throw new IllegalArgumentException(s"Unable to construct $host")
    }
  }
  private val PortRange = (1, 65535)
}