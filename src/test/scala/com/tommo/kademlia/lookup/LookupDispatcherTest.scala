package com.tommo.kademlia.lookup

import com.tommo.kademlia.routing.KBucketSetActor._
import akka.actor.{ Props, Actor, ActorRef }
import scala.concurrent.duration._
import com.tommo.kademlia.identity.Id
import akka.testkit.{ TestActorRef, TestProbe }
import LookupDispatcher._
import com.tommo.kademlia.util.RefreshActor._
import com.tommo.kademlia.util.EventSource._
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.BaseFixture
import com.tommo.kademlia.BaseTestKit

class LookupDispatcherTest extends BaseTestKit("LookupDispatcher") with BaseFixture {
  trait Fixture {
    val kProbe = TestProbe()
    val timerProbe = TestProbe()
    val lookupValueProbe = TestProbe()
    val lookupNodeProbe = TestProbe()

    trait MockProvider extends LookupNode.Provider with LookupValue.Provider {
      override def newLookupValueActor(selfNode: ActorNode, storeRef: ActorRef, kBucketRef: ActorRef, reqSender: ActorRef, kBucketSize: Int, alpha: Int, roundTimeOut: FiniteDuration) = wrapActorRef(lookupValueProbe.ref)
      override def newLookupNodeActor(selfNode: ActorNode, kBucketSetRef: ActorRef, reqSender: ActorRef, kBucketSize: Int, alpha: Int, roundTimeOut: FiniteDuration) = wrapActorRef(lookupNodeProbe.ref)
    }
    
    val verifyRef = TestActorRef[LookupDispatcher](Props(new LookupDispatcher(ActorNode(TestProbe().ref, id), TestProbe().ref, TestProbe().ref, kProbe.ref, timerProbe.ref) with MockProvider))

    def expectGetRandomIdInSameBucketAs(id: Id) = kProbe.fishForMessage() {
      case GetRandomIdInSameBucketAs(anId) if id == anId => true
      case _ => false
    }
  }

  test("on init query KBucketActor for number of buckets") {
    new Fixture {
      kProbe.expectMsg(GetNumKBuckets)
    }
  }

  test("after receiving number of buckets; get random id from kbucketset") {
    (
      new Fixture {
        verifyRef ! NumKBuckets(2)

        kProbe.fishForMessage()({
          case GetRandomId(List(0, 1)) => true
          case a => false
        })
      })
  }

  test("after receiving random ids; start refresh timer") {
    new Fixture {
      val randIds = List((0, Id("001")), (1, Id("010")))

      val refreshReq = randIds.map(p => RefreshBucketTimer(p._1, p._2, mockConfig.refreshStaleKBucket))

      verifyRef ! RandomId(randIds)
      
      timerProbe.expectMsgAllOf(refreshReq: _*)
    }
  }

  test("on RefreshBucket event, perform a node lookup") {
    new Fixture {
      verifyRef ! RefreshDone(2, Id("1010"))
      lookupNodeProbe.expectMsg(Id("1010"))
      expectGetRandomIdInSameBucketAs(Id("1010"))
    }
  }

  test("on value lookup -> invoke value producer") {
    new Fixture {
      verifyRef ! LookupValue.FindValue(Id("1010"))
      lookupValueProbe.expectMsg(Id("1010"))
      expectGetRandomIdInSameBucketAs(Id("1010"))
    }
  }

  test("on nook lookup -> invoke node producer") {
    new Fixture {
      verifyRef ! LookupNode.FindKClosest(Id("1010"))
      lookupNodeProbe.expectMsg(Id("1010"))
      expectGetRandomIdInSameBucketAs(Id("1010"))
    }
  }
}