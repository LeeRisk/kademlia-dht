package com.tommo.kademlia.routing

import com.tommo.kademlia.misc.time.Epoch
import com.tommo.kademlia.protocol.Node

case class TimeStampNode(node: Node, time: Epoch) 

case class LastSeenOrdering extends Ordering[TimeStampNode] {
  override def compare(x: TimeStampNode, y: TimeStampNode) = x.time compareTo y.time
}