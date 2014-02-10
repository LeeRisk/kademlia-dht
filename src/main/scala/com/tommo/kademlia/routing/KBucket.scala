package com.tommo.kademlia.routing

import scala.collection.immutable.TreeSet

import com.tommo.kademlia.misc.time.Clock
import com.tommo.kademlia.protocol.Node

class KBucket(val capacity: Int)(implicit nodeEvictionOrder: Ordering[TimeStampNode]) {
  self: Clock =>

  type T <: Node

  var nodes = TreeSet[TimeStampNode]()

  def add(node: T) = {
    def newTimeStampNode = TimeStampNode(node, getTime())
    applyFnToFoundNode({
      case Some(existingNode) => updateNodesRef(nodes - existingNode + newTimeStampNode)
      case None if size < capacity => updateNodesRef(nodes + newTimeStampNode)
    })(node)
  }

  def remove(node: T) = applyFnToFoundNode({ case Some(existingNode) => updateNodesRef(nodes - existingNode) })(node)

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

  def getLowestOrder = nodes.min.node.asInstanceOf[T]

  def getNodes: List[T] = getNodes(size)
  def getNodes(numNodes: Int): List[T] = nodes.take(numNodes).map(_.node.asInstanceOf[T]).toList

  def size = nodes.size

  def isFull = size >= capacity

}

