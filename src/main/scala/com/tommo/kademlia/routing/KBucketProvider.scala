package com.tommo.kademlia.routing

import com.tommo.kademlia.protocol.Node
import com.tommo.kademlia.misc.time.SystemClock


trait KBucketProvider {
  
  type T <: Node
  
  def capacity: Int
  
  def newKBucketEntry = new KBucket(capacity)(LastSeenOrdering()) with SystemClock {
    type T = KBucketProvider#T
  }
}