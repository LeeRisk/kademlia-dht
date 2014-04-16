package com.tommo.kademlia.routing

import com.tommo.kademlia.protocol.Node
import com.tommo.kademlia.misc.time.SystemClock

trait KBucketProvider {
  def capacity: Int
  def newKBucketEntry[T <: Node] : KBucket[T] = new KBucket[T](capacity)(LastSeenOrdering()) with SystemClock
}