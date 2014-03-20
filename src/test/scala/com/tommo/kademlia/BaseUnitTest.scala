package com.tommo.kademlia

import org.scalatest._

import com.tommo.kademlia.protocol.Host
import com.tommo.kademlia.identity._

import scala.concurrent.duration._
import akka.actor._

trait BaseUnitTest extends FlatSpecLike with Matchers with OptionValues with Inside with Inspectors {
  class TestKadConfig extends KadConfig {
    val host = mockHost
    val kBucketSize = 10
    val addressSpace = 10
    val concurrency = 2
    val roundTimeOut = 1 milliseconds
    val responseTimeout = 600 milliseconds
  }

  implicit val mockConfig = new TestKadConfig

  def mockHost = Host("hostname:9009")

  def mockZeroId(size: Int) = Id((for (i <- 1 to size) yield ('0'))(collection.breakOut))
}