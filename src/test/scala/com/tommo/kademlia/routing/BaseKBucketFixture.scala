package com.tommo.kademlia.routing

import com.tommo.kademlia.misc.time.Clock
import com.tommo.kademlia.protocol.Node
import com.tommo.kademlia.identity.Id

trait BaseKBucketFixture {
  import java.util.UUID.randomUUID

  trait IncrementingClock extends Clock {
    var counter = 0
    def getTime() = { counter += 1; counter }
  }

  def withCapacity[N <: Node](capacity: Int) = new KBucket[N](capacity)(LastSeenOrdering()) with IncrementingClock 

  def aRandomId = Id(randomUUID.toString.getBytes())

  trait MockKBucketProvider extends KBucketProvider {
    val capacity = 2
    override def newKBucketEntry[T <: Node] = withCapacity[T](capacity)
  }
}