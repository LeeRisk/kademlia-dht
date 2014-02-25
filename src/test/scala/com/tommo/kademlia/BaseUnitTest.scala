package com.tommo.kademlia

import org.scalatest._

import com.tommo.kademlia.protocol.Host
import com.tommo.kademlia.identity._

import scala.concurrent.duration._
import akka.actor._

trait BaseUnitTest extends FlatSpecLike with Matchers with OptionValues with Inside with Inspectors {
    implicit val mockConfig = new KadConfig {
    val host = mockHost
    val kBucketSize = 10
    val addressSpace = 10
    val concurrency = 2
    val timeout = 500 milliseconds
  }
  
  
  def mockHost = Host("hostname:9009")
  
  def mockZeroId(size: Int) = Id((for (i <- 1 to size) yield ('0'))(collection.breakOut))
}