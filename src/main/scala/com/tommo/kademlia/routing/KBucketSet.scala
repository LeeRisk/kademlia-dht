package com.tommo.kademlia.routing

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.Node

class KBucketSet[T <: Node](id: Id) {
  self: KBucketProvider =>
    
  val kBucketArr = Array.fill(id.size)(newKBucketEntry)

  def apply(index: Int) = kBucketArr(index).getNodes

  val addressSize = id.size

  def add(node: T)(implicit repl: Node => Boolean) {
    val lp = id.longestPrefixLength(node.id)
    val kbucket = kBucketArr(addressSize - lp - 1)

    if (kbucket.isFull) {
      val lowestNode = kbucket.getLowestOrder
      if (repl(lowestNode)) {
        kbucket.remove(lowestNode)
        kbucket.add(node)
      }
    } else
      kbucket.add(node)
  }

  def getClosestInOrder(k: Int = kBucketArr(0).capacity, node: T) = {
    val indices = id.scanLeftPrefix(node.id)
    val diff = Stream.range(0, addressSize, 1).diff(indices)

    val traverseOrder = indices.toStream ++ diff

    def buildKClosest(count: Int = 0, acc: List[T] = List[T](), traverseOrder: Stream[Int] = traverseOrder): List[T] = {
      if (count < k && !traverseOrder.isEmpty) {
        val bucketIndex = traverseOrder.head
        val kbucket = kBucketArr(bucketIndex)
        val nodes = kbucket.getNodes.slice(0, k - count)
        buildKClosest(count + nodes.size, acc ++ nodes, traverseOrder.tail)
      } else
        acc
    }

    buildKClosest()
  }
}
