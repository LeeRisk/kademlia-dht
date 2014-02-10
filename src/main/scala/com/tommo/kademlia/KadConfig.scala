package com.tommo.kademlia

import com.typesafe.config._
import com.tommo.kademlia.protocol.Host
import TypeSafeKadConfig._

trait KadConfig {
	def host: Host
	def kBucketSize: Int
	def addressSpace: Int
}

class TypeSafeKadConfig(config: Config) extends KadConfig {
  def this() = this(ConfigFactory.load())

  config.checkValid(ConfigFactory.defaultReference(), namespace)

  override def host = Host(config.getString(s"${namespace}.host"), config.getInt(s"${namespace}.port"))

  override def kBucketSize = config.getInt(s"${namespace}.kbucket-size")

  override def addressSpace = config.getInt(s"${namespace}.address-space")
}

object TypeSafeKadConfig {
  private def namespace = "tommo-kad"
  def apply() = new TypeSafeKadConfig()
}