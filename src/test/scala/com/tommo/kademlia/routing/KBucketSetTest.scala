package com.tommo.kademlia.routing

import com.tommo.kademlia.BaseUnitTest
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol._

class KBucketSetTest extends BaseUnitTest {

  trait Fixture {
    implicit val selfId = Id("0000")

    def alwaysTrue(node: Node) = true

    trait SmallKBucketProvider extends KBucketProvider {
      override def newKBucketEntry = KBucket(2)
    }

    val bucketSet = new KBucketSet(selfId) with SmallKBucketProvider

    def addNode(nodes: List[Node], replaceFn: Node => Boolean = alwaysTrue) {
      for (node <- nodes) {
        Thread.sleep(1) // resolution of SystemClock is 1ms
        bucketSet.add(node)(replaceFn)
      }
    }

    def aNode(bitStr: String) = Node(Host("hostname:999"), Id(bitStr))
  }

  "A KBucket length" should "match it's own id size" in new Fixture {
    assert(bucketSet.addressSize == 4)
  }

  it should "add a node in the correct Kbucket relative to its ID" in new Fixture {
    val (closest, middle, farthest) = (aNode("0001"), aNode("0010"), aNode("1111"));
    addNode(List(closest, middle, farthest))

    bucketSet(0) should contain theSameElementsAs List(closest)
    bucketSet(1) should contain theSameElementsAs List(middle)
    bucketSet(3) should contain theSameElementsAs List(farthest)
  }

  it should "replace lowest node if replace fn returns true" in new Fixture {
    val (first, second, third) = (aNode("1000"), aNode("1001"), aNode("1011"))
    addNode(List(first, second, third))

    bucketSet(3) should contain theSameElementsAs List(third, second)
  }

  it should "replace lowest node if replace fn returns false" in new Fixture {
    val (first, second, third) = (aNode("1000"), aNode("1001"), aNode("1011"))
    addNode(List(first, second, third), x => false)

    bucketSet(3) should contain theSameElementsAs List(first, second)
  }

  it should "get the kth closest ids" in new Fixture {
    val id = aNode("0101")

    val nodesOrderedClosest = List(aNode("0100"), aNode("0001"), aNode("0010"), aNode("1000"))
    addNode(nodesOrderedClosest)

    bucketSet.getClosestInOrder(5, id) should contain theSameElementsInOrderAs nodesOrderedClosest
  }
}