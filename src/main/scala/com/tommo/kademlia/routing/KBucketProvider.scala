package com.tommo.kademlia.routing

import com.tommo.kademlia.protocol.Node
import com.tommo.kademlia.misc.time.SystemClock


trait KBucketProvider {
  def capacity: Int
  
  type T <: Node
  
  def newKBucketEntry = new KBucket[T](capacity)(LastSeenOrdering()) with SystemClock
}