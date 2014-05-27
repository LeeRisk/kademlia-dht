package com.tommo.kademlia.protocol

import com.tommo.kademlia.{ BaseTestKit, BaseFixture }
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.RequestDispatcher._
import com.tommo.kademlia.store.StoreActor._

import scala.concurrent.duration._
import akka.actor.{ Actor, ActorRef, Props }
import akka.testkit.{ TestKit, TestProbe, TestActorRef, TestActor }

import Message._

class RequestHandlerTest extends BaseTestKit("RequestHandlerSpec") with BaseFixture {

  trait Fixture {
    val selfProbe = TestProbe()
    val kSetProbe = TestProbe()
    val storeProbe = TestProbe()

    val verifyRef = TestActorRef[RequestHandler[Int]](Props(new RequestHandler(selfProbe.ref, kSetProbe.ref, storeProbe.ref)))

  }

  test("PingRequest respond with AckReply") {
    new Fixture {
      verifyRef ! PingRequest
      expectMsg(AckReply)
    }
  }

  test("KClosestRequest respond with KClosestReply") {
    new Fixture {
      val req = KClosestRequest(aRandomId, 4)
      val reply = KClosestReply(aRandomId, ActorNode(TestProbe().ref, aRandomId) :: Nil)

      kSetProbe.setAutoPilot(new TestActor.AutoPilot {
        def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = msg match {
          case KClosestRequest(aRandomId, 4) =>
            sender ! reply
            TestActor.NoAutoPilot
          case _ => TestActor.NoAutoPilot
        }
      })

      verifyRef ! req

      kSetProbe.expectMsg(req)
      expectMsg(reply)

    }
  }

  test("FindValueRequest respond with Left(kclosest) if no value exists in store") {
    new Fixture {
      val anId = aRandomId

      val req = FindValueRequest(anId, 10)
      val kclosestRequest = KClosestRequest(anId, 10)
      val kclosestReply = KClosestReply(id, ActorNode(TestProbe().ref, aRandomId) :: Nil)

      val reply = FindValueReply(Left(kclosestReply))

      storeProbe.setAutoPilot(new TestActor.AutoPilot {
        def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = msg match {
          case Get(anId) =>
            sender ! GetResult(None)
            TestActor.NoAutoPilot
          case _ => TestActor.NoAutoPilot
        }
      })

      kSetProbe.setAutoPilot(new TestActor.AutoPilot {
        def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = msg match {
          case KClosestRequest(anId, 10) =>
            sender ! kclosestReply
            TestActor.NoAutoPilot
          case _ => TestActor.NoAutoPilot
        }
      })

      verifyRef ! req
      storeProbe.expectMsg(Get(anId))
      kSetProbe.expectMsg(kclosestRequest)
      expectMsg(reply)
    }
  }

  test("FindValueRequest respond with Right(RemoteValue) if value exists") {
    new Fixture {
      val anId = aRandomId

      val req = FindValueRequest(anId, 10)
      val kclosestRequest = KClosestRequest(anId, 10)
      val kclosestReply = KClosestReply(id, ActorNode(TestProbe().ref, aRandomId) :: Nil)

      val reply = FindValueReply(Right(RemoteValue(300, 10 seconds)))

      storeProbe.setAutoPilot(new TestActor.AutoPilot {
        def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = msg match {
          case Get(anId) =>
            sender ! GetResult(Some((300, 10 seconds)))
            TestActor.NoAutoPilot
          case _ => TestActor.NoAutoPilot
        }
      })

      verifyRef ! req
      
      storeProbe.expectMsg(Get(anId))
      expectMsg(reply)
    }
  }

  test("CacheStoreRequest send to store") {
    new Fixture {
      val req = CacheStoreRequest(aRandomId, RemoteValue(3, 10 seconds))
      verifyRef ! req

      storeProbe.expectMsg(req)

    }
  }

  test("StoreRequest send to store") {
    new Fixture {
      val req = StoreRequest(aRandomId, RemoteValue(3, 10 seconds), 0)

      verifyRef ! req

      storeProbe.expectMsg(req)
    }
  }

}
