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
	def addressSpace: Int
	def roundConcurrency: Int
	def requestTimeOut: FiniteDuration
	def roundTimeOut: FiniteDuration
	def refreshStaleKBucket: FiniteDuration
	def refreshStore: FiniteDuration
	def id: Id
}

class TypeSafeKadConfig(config: Config) extends KadConfig {
  def this() = this(ConfigFactory.load())

  config.checkValid(ConfigFactory.defaultReference(), namespace)

  val host = Host(config.getString(s"${namespace}.host"), config.getInt(s"${namespace}.port"))
  
  val requestTimeOut = FiniteDuration(config.getInt(s"${namespace}.request-timeout-ms"), MILLISECONDS)
  
  val refreshStaleKBucket = FiniteDuration(config.getInt(s"${namespace}.kbucket-stale-seconds"), SECONDS)

  val refreshStore = FiniteDuration(config.getInt(s"${namespace}.store-refresh-seconds"), SECONDS)
  
  val kBucketSize = config.getInt(s"${namespace}.kbucket-size")

  val addressSpace = config.getInt(s"${namespace}.address-space")
  
  val roundConcurrency = config.getInt(s"${namespace}.round-concurrency")
  
  val roundTimeOut = FiniteDuration(config.getInt(s"${namespace}.round-timeout-ms"), MILLISECONDS)
  
  val id = Id(config.getString(s"${namespace}.id")) // TODO use provider to get id
}

object TypeSafeKadConfig {
  private def namespace = "tommo-kad"
  def apply() = new TypeSafeKadConfig()
}