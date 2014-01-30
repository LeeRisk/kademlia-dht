package com.tommo.kademlia.routing

import com.tommo.kademlia.misc.time.Clock.Epoch
import com.tommo.kademlia.protocol.AbstractNode

case class TimeStampNode(node: AbstractNode, time: Epoch) 

case class LastSeenOrdering extends Ordering[TimeStampNode] {
  override def compare(x: TimeStampNode, y: TimeStampNode) = x.time compareTo y.time
}