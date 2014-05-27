package com.tommo.kademlia.routing

import akka.actor.Props
import scala.concurrent.duration._
import akka.testkit.{ TestActorRef, TestProbe }

import KBucketRefresher._
import com.tommo.kademlia.util.RefreshActor._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.util.EventSource._
import com.tommo.kademlia.BaseFixture
import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.lookup.LookupNodeFSM.FindKClosest
import com.tommo.kademlia.routing.KBucketSetActor._

class KBucketRefresherTest extends BaseTestKit("LookupDispatcher") with BaseFixture {
  trait Fixture {
    val kProbe = TestProbe()
    val timerProbe = TestProbe()
    val lookupValueProbe = TestProbe()
    val lookupNodeProbe = TestProbe()

    val verifyRef = TestActorRef[KBucketRefresher](Props(new KBucketRefresher(lookupNodeProbe.ref, lookupValueProbe.ref, kProbe.ref, timerProbe.ref)))
  }

  test("on init register listeners") {
    new Fixture {
      lookupValueProbe.expectMsg(RegisterListener(verifyRef))
      lookupNodeProbe.expectMsg(RegisterListener(verifyRef))
    }
  }

  test("after receiving random ids; start refresh timer") {
    new Fixture {
      val randIds = List((0, Id("001")), (1, Id("010")))

      val refreshReq = randIds.map(p => RefreshBucketTimer(p._1, p._2, mockConfig.refreshStaleKBucket))

      verifyRef ! RandomId(randIds)

      timerProbe.expectMsgAllOf(refreshReq: _*)
    }
  }

  test("on RefreshDone, perform a node lookup") {
    new Fixture {
      verifyRef ! RefreshDone(2, Id("1010"))
      
      lookupNodeProbe.fishForMessage() {
        case FindKClosest(Id("1010")) => true
        case _ => false
      }
    }
  }
}