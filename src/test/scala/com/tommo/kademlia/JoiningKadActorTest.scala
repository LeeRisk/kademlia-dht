package com.tommo.kademlia

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.routing.{ KBucketSetActor, KBucketRefresher }
import com.tommo.kademlia.protocol.{ RequestDispatcher, ActorNode }
import com.tommo.kademlia.store.StoreActor
import com.tommo.kademlia.protocol.BaseProtocolFixture
import com.tommo.kademlia.protocol.Message.AuthReceiverRequest
import akka.actor.{ Actor, ActorRef, Props }
import akka.testkit.{ TestProbe, TestFSMRef }
import scala.concurrent.duration.Duration
import com.tommo.kademlia.store.StoreActor.{ Get, Insert }
import com.tommo.kademlia.protocol.RequestDispatcher.NodeRequest
import com.tommo.kademlia.lookup.{ LookupDispatcher, LookupValueFSM }
import com.tommo.kademlia.lookup.LookupNodeFSM._
import JoiningKadActor._

class JoiningKadActorTest extends BaseTestKit("JoiningSpec") with BaseFixture {

  trait Fixture {
    val requestProbe = TestProbe()
    val lookupNodeProbe = TestProbe()
    val kProbe = TestProbe()

    trait MockProvider extends KBucketRefresher.Provider with LookupDispatcher.Provider with KBucketSetActor.Provider with RequestDispatcher.Provider with StoreActor.Provider[Int] {
      override def newKBucketRefresher(nodeDispatcher: ActorRef, valueDispatcher: ActorRef, kBucketRef: ActorRef, refreshRef: ActorRef)(implicit config: KadConfig) = wrapActorRef(TestProbe().ref)
      override def newLookupValueDispatcher(selfNode: ActorNode, kBucketRef: ActorRef, storeRef: ActorRef)(implicit config: KadConfig) = wrapActorRef(TestProbe().ref)
      override def newKBucketSetActor(selfNode: ActorNode)(implicit kadConfig: KadConfig) = wrapActorRef(kProbe.ref)
      override def newRequestDispatcher(selfNode: ActorNode, kSet: ActorRef, reqHandlerRef: ActorRef, timeout: Duration) = wrapActorRef(requestProbe.ref)
      override def storeActor(selfNode: ActorNode, kBucketRef: ActorRef, timerRef: ActorRef, lookupRef: ActorRef)(implicit config: KadConfig) = wrapActorRef(TestProbe().ref)
      override def newLookupNodeDispatcher(selfNode: ActorNode, kBucketRef: ActorRef)(implicit config: KadConfig) = wrapActorRef(lookupNodeProbe.ref)
    }

    val verifyRef = TestFSMRef(new JoiningKadActor[Int](id) with MockProvider)
  }

  test("start with WaitForExisting") {
    new Fixture {
      verifyRef.stateName shouldBe WaitForExisting
    }
  }

  test("when existing ActorRef received send a PingRequest to get id") {
    new Fixture {
      import com.tommo.kademlia.protocol.Message.PingRequest

      val remoteProbe = TestProbe()

      verifyRef ! remoteProbe.ref

      requestProbe.expectMsg(NodeRequest(remoteProbe.ref, PingRequest))
    }
  }

  test("when AckReply received lookup kclosest for self id") {
    new Fixture {
      import com.tommo.kademlia.protocol.Message.AckReply
      verifyRef.setState(Joining)

      verifyRef ! AckReply

      lookupNodeProbe.expectMsg(FindKClosest(id))
    }
  }

  test("Joining state should be able to handle NodeRequest") {
    new Fixture with BaseProtocolFixture {
      val nodeRequest = NodeRequest(TestProbe().ref, MockRequest())

      verifyRef.setState(Joining)
      verifyRef ! nodeRequest

      requestProbe.expectMsg(nodeRequest)
      requestProbe.lastSender shouldBe self
    }
  }

  test("when initial kclosest result received; get LowestNonEmptyBucket") {
    new Fixture {
      import com.tommo.kademlia.routing.KBucketSetActor.GetLowestNonEmpty
      verifyRef.setState(Joining)

      verifyRef ! Result(id, Nil)
      kProbe.expectMsg(GetLowestNonEmpty)
    }
  }

  test("when LowestNonEmpty received get randomIds for each from (kBucketSize, lowestIndex]") {
    new Fixture {
      import com.tommo.kademlia.routing.KBucketSetActor.{ LowestNonEmpty, GetRandomId }
      verifyRef.setState(Joining)
      val lowest = 2
      val randIndices = lowest until mockConfig.kBucketSize

      verifyRef ! LowestNonEmpty(lowest)

      kProbe.expectMsg(GetRandomId(randIndices.toList))

    }
  }

  test("when randomIds received query kclosest for all and go to Running state") {
    new Fixture {
      import com.tommo.kademlia.routing.KBucketSetActor.RandomId
      import KadActor._

      verifyRef.setState(Joining)

      val ids = RandomId((1, aRandomId) :: (2, aRandomId) :: Nil)

      val expected = ids.randIds.map(x => FindKClosest(x._2))

      verifyRef ! ids

      lookupNodeProbe.expectMsgAllOf(expected: _*)
      
      verifyRef.stateName shouldBe Running
    }
  }

  test("stash messages until node is in Running State") {

  }
}