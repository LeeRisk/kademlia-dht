package com.tommo.kademlia.util

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.misc.time.Clock
import com.tommo.kademlia.misc.time.Clock.Epoch
import com.tommo.kademlia.util.RefreshActor.Refresh

import akka.actor.Props
import akka.testkit.TestActorRef

import org.mockito.Matchers._
import org.mockito.Mockito._

class RefreshActorTest extends BaseTestKit("RefreshActorSpec") {

  trait Fixture {

    trait ZeroedClock extends Clock {
      override def getTime() = 0
    }

    val verifyRef = TestActorRef[RefreshActor](Props(new RefreshActor with ZeroedClock))

    def mockRequest(mockAfter: FiniteDuration, mockAt: Epoch = 0, mockKey: String = "key", mockEvent: String = "HELLO", mockRefreshKey: String = "mockRefresh") = new Refresh {
      val refreshKey = mockRefreshKey
      val key: Any = mockKey
      val event: Any = mockEvent
      override val at: Epoch = mockAt
      val after: FiniteDuration = mockAfter
    }
  }

  test("send message back after scheduled time") {
    new Fixture {
      val at = 120 millis

      verifyRef ! mockRequest(at)
      expectNoMsg(at)
      expectMsg("HELLO")
    }
  }

  test("message should be prioritized according the the scheduled time relative to the current time") {
    new Fixture {
      val earlier = 50 millis
      val later = 100 millis

      verifyRef ! mockRequest(later)
      verifyRef ! mockRequest(earlier, mockKey = "key2",mockEvent = "WORLD")
      expectMsg("WORLD")
      expectMsg("HELLO")
    }
  }

  test("if same time then schedule the one received first") {
    new Fixture {
      val earlier = 50 millis
      val later = 50 millis

      verifyRef ! mockRequest(earlier,  mockKey = "key2", mockEvent = "WORLD")
      verifyRef ! mockRequest(later)
      expectMsg("WORLD")
      expectMsg("HELLO")
    }
  }

  test("if messages with same refreshkey and key in regards to equals() received and one is scheduled; override using the latest") {
    new Fixture {
      verifyRef ! mockRequest(50 millis, mockEvent = "HELLO")
      verifyRef ! mockRequest(100 millis, mockEvent = "WORLD")
      verifyRef ! mockRequest(100 millis, mockEvent = "!!")
      
      expectMsg("!!")
      expectNoMsg()
    }

  }

}