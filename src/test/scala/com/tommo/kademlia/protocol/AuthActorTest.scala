package com.tommo.kademlia.protocol

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.routing.KBucketSetActor.Add

import akka.actor.Actor
import akka.actor.Props
import akka.testkit.TestActorRef
import akka.testkit.TestProbe

class AutoActorTest extends BaseTestKit("AuthSpec") {

  trait AuthFixture extends BaseProtocolFixture {
    val bucketProbe = TestProbe()

    class MockAuthActor extends AuthActor(bucketProbe.ref) {}

    def mockAuthReply(ref: TestActorRef[MockAuthActor]) = AuthSenderReply(mockZeroId(4), echoId = ref.underlyingActor.toEchoId)

    lazy val mockAuthActor: MockAuthActor = new MockAuthActor

    val verifyRef = TestActorRef[MockAuthActor](Props(mockAuthActor))
  }

  trait SenderAuthFixTure extends BaseProtocolFixture {
    val nodeProbe = TestProbe()
    val bucketProbe = TestProbe()

    val verifyRef = TestActorRef[SenderAuthActor](Props(new SenderAuthActor(mockZeroId(4), bucketProbe.ref, nodeProbe.ref)))
    verifyRef.underlyingActor.requestor = testActor
  }

  trait ReceiverAuthFixTure extends BaseProtocolFixture {
    val requestProbe = TestProbe()
    val bucketProbe = TestProbe()
    val selfProbe = TestProbe()

    val verifyRef = TestActorRef[ReceiverAuthActor](Props(new ReceiverAuthActor(bucketProbe.ref, requestProbe.ref, selfProbe.ref)))

    verifyRef.underlyingActor.requestor = testActor
  }

  "An AuthActor" should "first compose receive with authChallenge" in new AuthFixture {
    verifyRef.underlyingActor.init = false

    var inSubRecieve = false

    override lazy val mockAuthActor = new MockAuthActor { override def authChallenge(msg: Message) { inSubRecieve = true } }

    verifyRef ! MockRequest()

    inSubRecieve shouldBe true
  }

  it should "delegate to authSuccess if echoId matches toEchoId" in new AuthFixture {
    verifyRef.underlyingActor.init = true

    var inSubRecieve = false

    override lazy val mockAuthActor = new MockAuthActor { override def authSuccess(reply: AuthReply) = { inSubRecieve = true; reply shouldEqual mockAuth } }

    val mockAuth = mockAuthReply(verifyRef)

    verifyRef ! mockAuth

    inSubRecieve shouldBe true
  }

  it should "add sender to kBucketActor after confirming authencity" in new AuthFixture {
    verifyRef.underlyingActor.init = true
    val mockAuth = mockAuthReply(verifyRef)
    val msg = Add(ActorNode(self, mockZeroId(4)))

    verifyRef ! mockAuth

    bucketProbe.expectMsg(msg)
  }

  it should "save original sender of the AuthChallenge" in new AuthFixture {
    verifyRef ! MockRequest()
    awaitCond(verifyRef.underlyingActor.requestor == testActor)
  }

  "A SenderAuthActor" should "on receiving a RequestEnvelope forward an AuthSenderRequest to node" in new SenderAuthFixTure {
    verifyRef ! MockRequest()

    nodeProbe.expectMsg(AuthSenderRequest(MockRequest(), verifyRef.underlyingActor.toEchoId))
  }

  it should "forward reply to original actor that sent request message and send an ack to the receiver" in new SenderAuthFixTure {
    verifyRef.underlyingActor.init = true
    verifyRef ! AuthRecieverReply(MockReply(), verifyRef.underlyingActor.toEchoId, 1)

    expectMsg(MockReply())
    nodeProbe.expectMsg(AuthSenderReply(mockZeroId(4), 1))
  }

  "A ReceiverAuthActor" should "delegate to requestHandler Actor for request" in new ReceiverAuthFixTure {
    verifyRef ! AuthSenderRequest(MockRequest(), 1)
    requestProbe.expectMsg(MockRequest())
  }

  it should "send echo back the random id along with the result" in new ReceiverAuthFixTure {
    val expectedEcho = 1
    verifyRef.underlyingActor.toEchoBack = expectedEcho

    verifyRef ! MockReply()

    expectMsg(AuthRecieverReply(MockReply(), expectedEcho, verifyRef.underlyingActor.toEchoId))
  }

  it should "use the sender of the initial request as the sender when replying to the remote node" in new ReceiverAuthFixTure {
    verifyRef ! MockReply()

    expectMsgAnyClassOf(classOf[AuthRecieverReply]) // this syncs with the test otherwise an error will be thrown

    lastSender shouldBe selfProbe.ref
  }
}