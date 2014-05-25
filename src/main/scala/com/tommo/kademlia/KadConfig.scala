package com.tommo.kademlia

import com.typesafe.config._
import com.tommo.kademlia.protocol.Host
import TypeSafeKadConfig._
import scala.concurrent.duration._
import com.tommo.kademlia.identity.Id

import akka.util.Timeout

trait KadConfig {
	def host: Host
	def kBucketSize: Int
	def roundConcurrency: Int
	def requestTimeOut: FiniteDuration
	def roundTimeOut: FiniteDuration
	def refreshStaleKBucket: FiniteDuration
	def republishOriginal: FiniteDuration
	def republishRemote: FiniteDuration
	def expireRemote: FiniteDuration
}

class TypeSafeKadConfig(config: Config) extends KadConfig {
  def this() = this(ConfigFactory.load())

  config.checkValid(ConfigFactory.defaultReference(), namespace)

  val host = Host(config.getString(s"${namespace}.host"), config.getInt(s"${namespace}.port"))
  
  val requestTimeOut = FiniteDuration(config.getInt(s"${namespace}.request-timeout-milliseconds"), MILLISECONDS)
  
  val refreshStaleKBucket = FiniteDuration(config.getInt(s"${namespace}.kbucket-stale-seconds"), SECONDS)

  val republishOriginal = FiniteDuration(config.getInt(s"${namespace}.republish-original-seconds"), SECONDS)

  val republishRemote = FiniteDuration(config.getInt(s"${namespace}.republish-remote-seconds"), SECONDS)

  val expireRemote = FiniteDuration(config.getInt(s"${namespace}.expire-remote-seconds"), SECONDS)
  
  val kBucketSize = config.getInt(s"${namespace}.kbucket-size")

  val roundConcurrency = config.getInt(s"${namespace}.round-concurrency")
  
  val roundTimeOut = FiniteDuration(config.getInt(s"${namespace}.round-timeout-ms"), MILLISECONDS)
}

object TypeSafeKadConfig {
  private def namespace = "tommo-kad"
  def apply() = new TypeSafeKadConfig()
}