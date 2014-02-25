package com.tommo.kademlia

import com.typesafe.config._
import com.tommo.kademlia.protocol.Host
import TypeSafeKadConfig._
import scala.concurrent.duration._

trait KadConfig {
	def host: Host
	def kBucketSize: Int
	def addressSpace: Int
	def concurrency: Int
	def timeout: Duration
}

class TypeSafeKadConfig(config: Config) extends KadConfig {
  def this() = this(ConfigFactory.load())

  config.checkValid(ConfigFactory.defaultReference(), namespace)

  val host = Host(config.getString(s"${namespace}.host"), config.getInt(s"${namespace}.port"))

  val kBucketSize = config.getInt(s"${namespace}.kbucket-size")

  val addressSpace = config.getInt(s"${namespace}.address-space")
  
  val concurrency = config.getInt(s"${namespace}.concurrency")
  
  val timeout = Duration(config.getInt(s"${namespace}.timeout-ms"), MILLISECONDS)
}

object TypeSafeKadConfig {
  private def namespace = "tommo-kad"
  def apply() = new TypeSafeKadConfig()
}