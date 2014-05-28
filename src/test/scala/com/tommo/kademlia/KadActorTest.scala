package com.tommo.kademlia

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.routing.{ KBucketSetActor, KBucketRefresher}
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

class KadActorTest extends BaseTestKit("KadActorSpec") with BaseFixture {

  trait Fixture {
    val requestProbe = TestProbe()
    val valueProbe = TestProbe()
    val storeProbe = TestProbe()
    val kBucketProbe = TestProbe()

    trait MockProvider extends KBucketRefresher.Provider with LookupDispatcher.Provider with KBucketSetActor.Provider with RequestDispatcher.Provider with StoreActor.Provider[Int] {
      override def newKBucketRefresher(nodeDispatcher: ActorRef, valueDispatcher: ActorRef, kBucketRef: ActorRef, refreshRef: ActorRef)(implicit config: KadConfig) = wrapActorRef(TestProbe().ref)
      override def newLookupValueDispatcher(selfNode: ActorNode, kBucketRef: ActorRef, storeRef: ActorRef)(implicit config: KadConfig) = wrapActorRef(valueProbe.ref)
      override def newKBucketSetActor(selfNode: ActorNode)(implicit kadConfig: KadConfig) = wrapActorRef(kBucketProbe.ref)
      override def newRequestDispatcher(selfNode: ActorNode, kSet: ActorRef, reqHandlerRef: ActorRef, timeout: Duration) = wrapActorRef(requestProbe.ref)
      override def storeActor(selfNode: ActorNode, kBucketRef: ActorRef, timerRef: ActorRef, lookupRef: ActorRef)(implicit config: KadConfig) = wrapActorRef(storeProbe.ref)
    }

    val verifyRef = TestFSMRef(new KadActor[Int](id) with MockProvider)
  }
  
  test("initial state should be Running") {
    new Fixture {
      import KadActor._
      verifyRef.stateName shouldBe Running
    }
  }

  test("when AuthSenderRequest received forward to RequestDispatcher") {
    new Fixture with BaseProtocolFixture {
      val auth = AuthReceiverRequest(MockRequest(), 123)

      verifyRef ! auth

      requestProbe.expectMsg(auth)
      requestProbe.lastSender shouldBe self
    }
  }

  test("when LookupValue.FindValue recevied forward to LookupValueDispatcher") {
    new Fixture {
      val lookup = LookupValueFSM.FindValue(aRandomId)

      verifyRef ! lookup

      valueProbe.expectMsg(lookup)
      valueProbe.lastSender shouldBe self
    }
  }

  test("when Insert received forward to Store") {
    new Fixture {
      val insert = Insert(aRandomId, 3)

      verifyRef ! insert

      storeProbe.expectMsg(insert)
      storeProbe.lastSender shouldBe self
    }
  }

  test("when NodeRequest received forward to request dispatcher") {
    new Fixture with BaseProtocolFixture {
      val nodeRequest =  NodeRequest(TestProbe().ref, MockRequest())
      verifyRef ! nodeRequest

      requestProbe.expectMsg(nodeRequest)
      requestProbe.lastSender shouldBe self
    }
  }
}