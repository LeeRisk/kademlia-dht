package com.tommo.kademlia

import org.scalatest._

import com.tommo.kademlia.protocol.Host
import com.tommo.kademlia.identity._

import akka.actor._

trait BaseUnitTest extends FlatSpecLike with Matchers with OptionValues with Inside with Inspectors {
  def mockHost = Host("hostname:9009")
  
  def mockZeroId(size: Int) = Id((for (i <- 1 to size) yield ('0'))(collection.breakOut))
}