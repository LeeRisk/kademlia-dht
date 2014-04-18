package com.tommo.kademlia.routing

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.routing.KBucketSetActor._

import akka.actor.{ Props, Actor }
import akka.testkit.{TestActorRef, TestProbe}

class KBucketSetActorTest extends BaseTestKit("KBucketSpec") {

  trait Fixture extends BaseKBucketFixture {
    val mockRef = TestProbe().ref
    val mockKBucket: MockKBucketSet
    class MockKBucketSet extends KBucketSet[ActorNode](mockZeroId(4)) with MockKBucketProvider
    
    lazy val verifyRef = TestActorRef[KBucketSetActor](Props(new KBucketSetActor(mockKBucket)))

  }

  "A KBucketSetActor" should "return the KClosest to an id by calling KBucketSet's getClosestInOrder method" in new Fixture {
    var getClosestInvoked = false
    val expectedK = 2
    val mockResult = ActorNode(mockRef, aRandomId) :: ActorNode(mockRef, aRandomId) :: Nil

    val mockKBucket = new MockKBucketSet {
      override def getClosestInOrder(k: Int, id: Id) = {
        k shouldBe expectedK
        id shouldBe mockZeroId(4)
        getClosestInvoked = true
        mockResult
      }
    }
        
    verifyRef ! GetKClosest(mockZeroId(4), expectedK)
    expectMsg(KClosest(mockZeroId(4), mockResult))
    getClosestInvoked shouldBe true
  }
}