package com.tommo.kademlia.routing

import scala.collection.immutable.TreeSet

import com.tommo.kademlia.misc.time.Clock
import com.tommo.kademlia.protocol.Node

class KBucketEntry(val capacity: Int)(implicit nodeEvictionOrder: Ordering[TimeStampNode]) {
  self: Clock =>

  var nodes = TreeSet[TimeStampNode]()

  def add(node: Node) = {
    def newTimeStampNode = TimeStampNode(node, getTime())

    bleh({
      case Some(existingNode) => updateNodesRef(nodes - existingNode + newTimeStampNode)
      case None if size < capacity => updateNodesRef(nodes + newTimeStampNode)
    })(node)

  }

  def remove(node: Node) = bleh({ case Some(existingNode) => updateNodesRef(nodes - existingNode) })(node)

  private def bleh(matchedFunc: PartialFunction[Option[TimeStampNode], Unit])(node: Node): Boolean = {
    val foundNode = findNode(node)

    if (matchedFunc.isDefinedAt(foundNode)) {
      matchedFunc(foundNode)
      true
    } else
      false
  }

  private def updateNodesRef(op: => TreeSet[TimeStampNode]) = nodes = op

  private def findNode(node: Node) = nodes.find(_.node == node)

  def getHighestOrder = nodes.max.node
  def getLowestOrder = nodes.min.node

  def getNodes: List[Node] = getNodes(size)
  def getNodes(numNodes: Int) = nodes.take(numNodes).map(_.node).toList

  def size = nodes.size
}

object KBucketEntry {
  import com.tommo.kademlia.misc.time.SystemClock
  def apply(capacity: Int) = new KBucketEntry(capacity)(LastSeenOrdering()) with SystemClock
}