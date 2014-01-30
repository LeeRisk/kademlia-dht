package com.tommo.kademlia.routing

import com.tommo.kademlia.protocol.AbstractNode
import com.tommo.kademlia.misc.time.SystemClock


trait KBucketProvider {
  
  type T <: AbstractNode
  
  def capacity: Int
  
  def newKBucketEntry = new KBucket(capacity)(LastSeenOrdering()) with SystemClock {
    type T = KBucketProvider#T
  }
}