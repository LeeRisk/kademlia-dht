package com.tommo.kademlia.protocol

import akka.actor.{ Props, Actor }
import akka.testkit.{ TestActorRef, TestProbe }
import com.tommo.kademlia.protocol._
import com.tommo.kademlia.routing.KBucketSetActor.Add
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.SenderAuthActor
import com.tommo.kademlia.protocol.ReceiverAuthActor
import com.tommo.kademlia.protocol.AuthActor
import com.tommo.kademlia.BaseTestKit

class AutoActorTest extends BaseTestKit("AuthSpec") {
  trait AuthFixture {
    class MockAuthActor extends AuthActor(testActor) {
      def authChallenge: Actor.Receive = Actor.emptyBehavior
    }

    def mockAuthReply(ref: TestActorRef[MockAuthActor]) = AutoSenderReply(mockZeroId(4), echoId = ref.underlyingActor.toEchoId)
  }

  trait SenderAuthFixTure {
    val nodeProbe = TestProbe()
    val toForward = TestProbe()
    val verifyRef = TestActorRef[SenderAuthActor](Props(new SenderAuthActor(mockZeroId(4), testActor, nodeProbe.ref, toForward.ref)))
  }

  trait ReceiverAuthFixTure {
    val requestProbe = TestProbe()
    val toSend = TestProbe()
    val verifyRef = TestActorRef[SenderAuthActor](Props(new ReceiverAuthActor(testActor, requestProbe.ref, toSend.ref)))
  }

  "An AuthActor" should "use challengeReceive for auth challenge" in new AuthFixture {
    var inSubRecieve = false

    class MockChallenge extends MockAuthActor {
      override def authChallenge = { case _ => inSubRecieve = true }
    }

    val verifyRef = TestActorRef[MockAuthActor](Props(new MockChallenge))

    verifyRef ! "Some message doesn't matter"
    inSubRecieve shouldBe true
  }

  it should "delegate to authSuccess if echoId matches toEchoId" in new AuthFixture {
    var inSubRecieve = false

    class MockSuccess extends MockAuthActor {
      override def authSuccess(reply: AuthReply) = { inSubRecieve = true; reply shouldEqual mockAuth }
    }

    val verifyRef = TestActorRef[MockAuthActor](Props(new MockSuccess))

    val mockAuth = mockAuthReply(verifyRef)

    verifyRef ! mockAuth

    inSubRecieve shouldBe true
  }

  it should "add sender to kBucketActor after confirming authencity" in new AuthFixture {
    val verifyRef = TestActorRef[MockAuthActor](Props(new MockAuthActor))

    val mockAuth = mockAuthReply(verifyRef)

    val msg = Add(ActorNode(self, mockZeroId(4)))

    verifyRef ! mockAuth

    expectMsg(msg)
  }

  "A SenderAuthActor" should "on receiving a RequestEnvelope forward an AuthSenderRequest to node" in new SenderAuthFixTure {
    verifyRef ! PingRequest(mockZeroId(4))

    nodeProbe.expectMsg(AuthSenderRequest(PingRequest(mockZeroId(4)), verifyRef.underlyingActor.toEchoId))
  }

  it should "forward reply to original actor that sent request message and send an ack to the receiver" in new SenderAuthFixTure {
    verifyRef ! AuthRecieverReply(PingReply(mockZeroId(4)), verifyRef.underlyingActor.toEchoId, 1)

    toForward.expectMsg(PingReply(mockZeroId(4)))
    nodeProbe.expectMsg(AutoSenderReply(mockZeroId(4), 1))
  }

  "A ReceiverAuthActor" should "delegate to requestHandler Actor for request" in new ReceiverAuthFixTure {
    val expectedReq = PingRequest(Id("0001"))

    verifyRef ! AuthSenderRequest(expectedReq, 1)

    requestProbe.expectMsg(expectedReq)
  }

  it should "send echo back the random id along with the result" in new ReceiverAuthFixTure {
    val aReq = PingRequest(Id("0001"))
    val aRep = PingReply(mockZeroId(4))
    val expectedEcho = 1
    
    verifyRef ! AuthSenderRequest(aReq, expectedEcho)
    verifyRef ! aRep
    
    toSend.expectMsg(AuthRecieverReply(aRep, expectedEcho, verifyRef.underlyingActor.toEchoId))
  }
  
  it should "use the sender of the initial request as the sender when replying to the remote node" in new ReceiverAuthFixTure {
    verifyRef ! AuthSenderRequest(PingRequest(Id("0001")), 1)
    verifyRef ! PingReply(mockZeroId(4))
    
    toSend.expectMsgAnyClassOf(classOf[AuthRecieverReply]) // this syncs with the unit test otherwise an error will be thrown
    toSend.sender shouldBe testActor
  }
}