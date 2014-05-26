package com.tommo.kademlia

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.Host

import scala.util.Random
import scala.concurrent.duration._
import akka.actor._

trait BaseFixture {

  class TestKadConfig extends KadConfig {
    val name = "testKadSystem"
    val host = mockHost // don't change settings as it effects other tests; instead extend
    val kBucketSize = 4
    val addressSpace = 4
    val roundConcurrency = 2
    val refreshStaleKBucket = 1 second
    val republishOriginal = 24 hours
    val roundTimeOut = 10 seconds
    val requestTimeOut = 10 seconds
    val republishRemote = 1 hour
    val expireRemote = 10 hours
  }

  implicit val mockConfig = new TestKadConfig
  
  val id = mockZeroId(mockConfig.kBucketSize)

  def mockHost = Host("hostname:9009")

  def mockZeroId(size: Int) = Id((for (i <- 1 to size) yield ('0'))(collection.breakOut))

  def aRandomId = Id(Random.nextInt.toBinaryString)

  def aRandomId(size: Int) = Id(Random.nextInt.toBinaryString.take(size))

}