package com.tommo.kademlia.routing

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.Node
import scala.util.Random

class KBucketSet[T <: Node](id: Id) {
  self: KBucket.Provider =>

  val kBucketArr = Array.fill(id.size)(newKBucketEntry[T])

  def apply(index: Int) = kBucketArr(index).getNodes

  val addressSize = id.size

  def add(node: T) {
    if (isFull(node))
      throw new IllegalStateException("KBucket is already full")
    else {
      if (contains(node))
        remove(node)

      getKBucket(node).add(node)
    }
  }

  private[routing] def getKBucketIndex(nodeId: Id) = id.longestPrefixLength(nodeId)

  private def getKBucket(node: T) = kBucketArr(addressSize - getKBucketIndex(node.id) - 1)

  def getLowestOrder(node: T) = getKBucket(node).getLowestOrder

  def isFull(node: T) = getKBucket(node).isFull

  def remove(node: T) = getKBucket(node).remove(node)

  def contains(node: T) = !getKBucket(node).findNode(node).isEmpty
  
  def getRandomId(bucket: Int) = {
    require(bucket >= 0 && bucket < addressSize, s"Invalid bucket range $bucket")
    val commonPref = id.toString.take(addressSize - bucket - 1); // TODO can use lazy arr's as these values remain the same throughout
    val flipBit = id.flip.toString.charAt(bucket)

    bucket match { 
      case 0 => Id(commonPref + flipBit)
      case _ => Id(commonPref + flipBit + Id(bucket)(BigInt(bucket, Random)).toString)
    }
  }
  
  def getNodesBetween(anId: Id) = id.findAllNonMatchingFromRight(anId).map(kBucketArr(_).size).sum

  def getClosestInOrder(k: Int = kBucketArr(0).capacity, anId: Id): List[T] = {
    val indices = id.findAllNonMatchingFromRight(anId)
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

object KBucketSet {
  trait Provider {
    def newKSet[T <: Node](id: Id, kBucketCapacity: Int): KBucketSet[T] = new KBucketSet[T](id) with KBucket.Provider { val capacity = kBucketCapacity }
  }
}
