package com.tommo.kademlia.routing

import com.tommo.kademlia.misc.time.Clock
import com.tommo.kademlia.protocol.Node
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.BaseFixture

import scala.util.Random
import scala.math._

trait BaseKBucketFixture extends BaseFixture {
  trait IncrementingClock extends Clock {
    var counter = 0
    override def getTime() = { counter += 1; counter }
  }

  def withCapacity[N <: Node](capacity: Int) = new KBucket[N](capacity)(LastSeenOrdering()) with IncrementingClock

  trait MockKBucketProvider extends KBucket.Provider {
    def capacity = mockConfig.kBucketSize
    override def newKBucketEntry[T <: Node] = withCapacity[T](capacity)
  }
}