package com.tommo.kademlia

import com.tommo.kademlia.routing.KBucketSetActor._
import akka.actor.{ Actor, Props }
import com.tommo.kademlia.identity.Id
import akka.testkit.{ TestActorRef, TestProbe }
import LookupActor._
import com.tommo.kademlia.util.EventSource._

class LookupActorTest extends BaseTestKit("LookupActorSpec") with BaseFixture {
  trait Fixture {
    val kProbe = TestProbe()
    val timerProbe = TestProbe()

    val verifyRef = TestActorRef[LookupActor](Props(new LookupActor(kProbe.ref, timerProbe.ref)))
  }

  test("on init query KBucketActor for number of buckets") {
    new Fixture {
      kProbe.expectMsg(GetNumKBuckets)
    }
  }

  test("after receiving number of buckets; get random id from kbucketset") {
    new Fixture {
      verifyRef ! NumKBuckets(2)

      kProbe.fishForMessage() {
        case GetRandomId(List(0, 1)) => true
        case a => false
      }
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

  test("Send ready after initialized") {
    new Fixture {
      verifyRef ! RegisterListener(testActor)
      verifyRef ! RandomId(List((0, Id("000"))))
      
      expectMsg(Ready)
    }
  }

}