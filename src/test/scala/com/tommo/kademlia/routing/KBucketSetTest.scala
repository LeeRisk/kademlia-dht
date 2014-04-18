package com.tommo.kademlia.routing

import com.tommo.kademlia.BaseUnitTest
import java.util.concurrent.Executors
import scala.concurrent._

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol._

class KBucketSetTest extends BaseUnitTest {

  trait Fixture extends BaseKBucketFixture {
    implicit val selfId = mockZeroId(4)

    def alwaysTrue(node: Node) = true

    val bucketSet = new KBucketSet[RemoteNode](selfId) with MockKBucketProvider 

    def addNode(nodes: List[RemoteNode], replaceFn: Node => Boolean = alwaysTrue) {
      for (node <- nodes) {
        Thread.sleep(1) // resolution of SystemClock is 1ms
        bucketSet.add(node)(replaceFn)
      }
    }

    def aNode(bitStr: String) = RemoteNode(mockHost, Id(bitStr))
  }

  "KBucket" should "return the same addressSize as id self size" in new Fixture {
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
    val nodesOrderedClosest = List(aNode("0100"), aNode("0001"), aNode("0010"), aNode("1000"))
    addNode(nodesOrderedClosest)

    bucketSet.getClosestInOrder(5, Id("0101")) should contain theSameElementsInOrderAs nodesOrderedClosest
  }
}