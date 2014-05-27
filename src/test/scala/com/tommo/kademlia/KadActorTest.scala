package com.tommo.kademlia

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.lookup.LookupDispatcher
import com.tommo.kademlia.routing.KBucketSetActor
import com.tommo.kademlia.protocol.{ RequestDispatcher, ActorNode }
import com.tommo.kademlia.store.StoreActor
import com.tommo.kademlia.protocol.BaseProtocolFixture
import com.tommo.kademlia.protocol.Message.AuthSenderRequest
import akka.actor.{ Actor, ActorRef, Props }
import akka.testkit.{ TestProbe, TestActorRef }
import scala.concurrent.duration.Duration
import com.tommo.kademlia.store.StoreActor.{ Get, Insert }
import com.tommo.kademlia.protocol.RequestDispatcher.NodeRequest
import com.tommo.kademlia.lookup.LookupValue

class KadActorTest extends BaseTestKit("KadActorSpec") with BaseFixture {

  trait Fixture {
    val requestProbe = TestProbe()
    val lookupProbe = TestProbe()
    val storeProbe = TestProbe()
    val kBucketProbe = TestProbe()

    trait MockProvider extends LookupDispatcher.Provider with KBucketSetActor.Provider with RequestDispatcher.Provider with StoreActor.Provider[Int] {
      override def newLookupDispatcher(selfNode: ActorNode, kBucketRef: ActorRef, timerRef: ActorRef)(implicit config: KadConfig): Actor = wrapActorRef(lookupProbe.ref)
      override def newKBucketSetActor(selfNode: ActorNode)(implicit kadConfig: KadConfig): Actor = wrapActorRef(kBucketProbe.ref)
      override def newRequestDispatcher(selfNode: ActorNode, kSet: ActorRef, reqHandlerRef: ActorRef, timeout: Duration): Actor = wrapActorRef(requestProbe.ref)
      override def storeActor(selfNode: ActorNode, kBucketRef: ActorRef, timerRef: ActorRef, lookupRef: ActorRef)(implicit config: KadConfig) = wrapActorRef(storeProbe.ref)
    }

    val verifyRef = TestActorRef[KadActor[Int]](Props(new KadActor[Int](id) with MockProvider))

  }

  test("when AuthSenderRequest received forward to RequestDispatcher") {
    new Fixture with BaseProtocolFixture {
      val auth = AuthSenderRequest(MockRequest(), 123)

      verifyRef ! auth

      requestProbe.expectMsg(auth)
      requestProbe.lastSender shouldBe self
    }
  }

  test("when LookupValue.FindValue recevied forward to LookupDispatcher") {
    new Fixture {
      val lookup = LookupValue.FindValue(aRandomId)

      verifyRef ! lookup

      lookupProbe.expectMsg(lookup)
      lookupProbe.lastSender shouldBe self

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

  test("when Get received forward to Store") {
    new Fixture {
      val get = Get(aRandomId)

      verifyRef ! get

      storeProbe.expectMsg(get)
      storeProbe.lastSender shouldBe self
    }
  }

  test("when NodeRequest received forward to request dispatcher") {
    new Fixture with BaseProtocolFixture{
      val nodeRequest =  NodeRequest(TestProbe().ref, MockRequest())
      verifyRef ! nodeRequest

      requestProbe.expectMsg(nodeRequest)
      requestProbe.lastSender shouldBe self
    }
  }
}