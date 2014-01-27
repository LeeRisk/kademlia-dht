package com.tommo.kademlia.routing

import scala.collection.immutable.TreeSet

import com.tommo.kademlia.misc.time.Clock
import com.tommo.kademlia.protocol.Node

class KBucket(val capacity: Int)(implicit nodeEvictionOrder: Ordering[TimeStampNode]) {
  self: Clock =>

  var nodes = TreeSet[TimeStampNode]()

  def add(node: Node) = {
    def newTimeStampNode = TimeStampNode(node, getTime())

    applyFnToFoundNode({
      case Some(existingNode) => updateNodesRef(nodes - existingNode + newTimeStampNode)
      case None if size < capacity => updateNodesRef(nodes + newTimeStampNode)
    })(node)

  }

  def remove(node: Node) = applyFnToFoundNode({ case Some(existingNode) => updateNodesRef(nodes - existingNode) })(node)

  private def applyFnToFoundNode(fn: PartialFunction[Option[TimeStampNode], Unit])(node: Node): Boolean = {
    val foundNode = findNode(node)

    if (fn.isDefinedAt(foundNode)) {
      fn(foundNode)
      true
    } else
      false
  }

  private def updateNodesRef(op: => TreeSet[TimeStampNode]) = nodes = op

  private def findNode(node: Node) = nodes.find(_.node == node)

  def getLowestOrder = nodes.min.node

  def getNodes: List[Node] = getNodes(size)
  def getNodes(numNodes: Int) = nodes.take(numNodes).map(_.node).toList

  def size = nodes.size
  
  def isFull = size >= capacity
  
}

object KBucket {
  import com.tommo.kademlia.misc.time.SystemClock
  def apply(capacity: Int) = new KBucket(capacity)(LastSeenOrdering()) with SystemClock
}