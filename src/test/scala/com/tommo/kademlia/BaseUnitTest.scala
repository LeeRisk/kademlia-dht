package com.tommo.kademlia

import org.scalatest._

import com.tommo.kademlia.protocol.Host
import com.tommo.kademlia.identity._

import scala.concurrent.duration._
import akka.actor._

trait BaseUnitTest extends FlatSpecLike with Matchers with OptionValues with Inside with Inspectors {
  class TestKadConfig extends KadConfig {
    val host = mockHost // don't change settings as it effects other tests; instead extend
    val kBucketSize = 4
    val addressSpace = 4
    val concurrency = 2
    val roundTimeOut = 1 milliseconds
    val id = mockZeroId(kBucketSize)
  }

  implicit val mockConfig = new TestKadConfig

  def mockHost = Host("hostname:9009")

  def mockZeroId(size: Int) = Id((for (i <- 1 to size) yield ('0'))(collection.breakOut))
}