package com.tommo.kademlia.routing

import scala.collection.immutable.TreeSet

import com.tommo.kademlia.misc.time.Clock
import com.tommo.kademlia.protocol.Node

class KBucket(val capacity: Int)(implicit nodeEvictionOrder: Ordering[TimeStampNode]) {
  self: Clock =>

  var nodes = TreeSet[TimeStampNode]()

  def add(node: Node): Option[Node] = {
    def newTimeStampNode = TimeStampNode(node, getTime())

    bleh({
      case Some(existingNode) => updateNodesRef(nodes - existingNode + newTimeStampNode)
      case None if size < capacity => updateNodesRef(nodes + newTimeStampNode)
    })(node)
    
  }

  def remove(node: Node): Option[Node] = bleh({case Some(existingNode) => updateNodesRef(nodes - existingNode)})(node)
 
  private def bleh(matchedFunc: PartialFunction[Option[TimeStampNode], Unit])(node: Node): Option[Node] = {
    val foundNode = findNode(node)

    if(matchedFunc.isDefinedAt(foundNode)) {
    	matchedFunc(foundNode)
    	None
    } else
        Some(node)
  }

  private def updateNodesRef(op: => TreeSet[TimeStampNode]) = nodes = op

  private def findNode(node: Node) = nodes.find(_.node.id == node.id)

  def getHighestOrder = nodes.max

  def getNodes: List[Node] = getNodes(size)
  def getNodes(numNodes: Int) = nodes.take(numNodes).map(_.node).toList

  def size = nodes.size
}

object KBucket {
  implicit def defaultEviction = LastSeenOrdering()
}