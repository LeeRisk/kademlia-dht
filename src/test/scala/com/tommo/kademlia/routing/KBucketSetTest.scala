package com.tommo.kademlia.routing

import com.tommo.kademlia.BaseUnitTest
import java.util.concurrent.Executors
import scala.concurrent._

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol._

class KBucketSetTest extends BaseUnitTest with BaseKBucketFixture {

  trait Fixture {
    implicit val selfId = mockZeroId(4)

    def alwaysTrue(node: Node) = true

    val bucketSet = new KBucketSet[RemoteNode](selfId) with MockKBucketProvider

    def addNode(nodes: List[RemoteNode]) {
      for (node <- nodes) {
        Thread.sleep(1) // resolution of SystemClock is 1ms
        bucketSet.add(node)
      }
    }
  }

  def aNode(bitStr: String) = RemoteNode(mockHost, Id(bitStr))

  test("return the same addressSize as id self size") {
    new Fixture {
      assert(bucketSet.addressSize == 4)
    }
  }

  test("add a node in the correct Kbucket relative to its ID") {
    new Fixture {
      val (closest, middle, farthest) = (aNode("0001"), aNode("0010"), aNode("1111"));
      addNode(List(closest, middle, farthest))

      bucketSet(0) should contain theSameElementsAs List(closest)
      bucketSet(1) should contain theSameElementsAs List(middle)
      bucketSet(3) should contain theSameElementsAs List(farthest)
    }
  }

  test("get the kth closest ids") {
    new Fixture {
      val nodesOrderedClosest = List(aNode("0100"), aNode("0001"), aNode("0010"), aNode("1000"))
      addNode(nodesOrderedClosest)

      bucketSet.getClosestInOrder(5, Id("0101")) should contain theSameElementsInOrderAs nodesOrderedClosest
    }
  }
  
  test("generate random id for specified kBucket") {
    new Fixture {
    	val totalBuckets = bucketSet.addressSize
    	
    	val randIds = for(x <- 0 until totalBuckets) yield (totalBuckets - x - 1, bucketSet.getRandomId(x))
    	
    	randIds.foreach({
    	  case (bucket, randId) => bucketSet.getKBucketIndex(aNode(randId.toString)) shouldBe bucket
    	})
    }
  }
}