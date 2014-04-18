package com.tommo.kademlia.routing

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.Node

class KBucketSet[T <: Node](id: Id) {
  self: KBucket.Provider =>

  val kBucketArr = Array.fill(id.size)(newKBucketEntry[T])

  def apply(index: Int) = kBucketArr(index).getNodes

  val addressSize = id.size

  def add(node: T) {
    if (isFull(node))
      throw new IllegalStateException("KBucket is already full")
    else
      getKBucketIndex(node).add(node)
  }

  private def getKBucketIndex(node: T) = {
    val lp = id.longestPrefixLength(node.id)
    kBucketArr(addressSize - lp - 1)
  }

  def getLowestOrder(node: T) = getKBucketIndex(node).getLowestOrder

  def isFull(node: T) = getKBucketIndex(node).isFull

  def remove(node: T) = getKBucketIndex(node).remove(node)

  def getClosestInOrder(k: Int = kBucketArr(0).capacity, anId: Id): List[T] = {
    val indices = id.findAllNonMatchingFromRight(anId)
    val diff = Stream.range(0, addressSize, 1).diff(indices)

    val traverseOrder = indices.toStream ++ diff
    println(indices)
    println(indices ++ diff);

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
