package com.tommo.kademlia.protocol

import akka.actor.{ Actor, ActorRef, ActorLogging }
import com.tommo.kademlia.protocol._
import com.tommo.kademlia.routing.KBucketSetActor.Add
import com.tommo.kademlia.identity.Id
import scala.util.Random
import akka.actor.actorRef2Scala

abstract class AuthActor(kBucketActor: ActorRef) extends Actor with ActorLogging {
  val toEchoId = Random.nextInt

  var requestor = context.system.deadLetters
  var init = false

  final def receive = saveSenderChallenge orElse authCheck orElse unknownMsg

  private def saveSenderChallenge: Actor.Receive = {
    case a: Message if !init =>
      init = true
      requestor = sender
      authChallenge(a)
  }

  protected def authChallenge(msg: Message) = {}

  protected final def authCheck: Actor.Receive = {
    case reply: AuthReply if reply.echoId == toEchoId =>
      authSuccess(reply)
      kBucketActor ! Add(ActorNode(sender, reply.sender))
      finish()
  }

  private def unknownMsg: Actor.Receive = {
    case unknown =>
      log.warning(s"Received unknown message when doing auth check: $unknown")
      finish()
  }

  private def finish() {
    context.stop(self)
  }

  protected def authSuccess(reply: AuthReply) {}
}

private[protocol] class SenderAuthActor(val id: Id, kBucketActor: ActorRef, node: ActorRef) extends AuthActor(kBucketActor) {
  override protected def authChallenge(msg: Message) = msg match {
    case req: Request => node ! AuthSenderRequest(req, toEchoId)
  }

  override protected def authSuccess(reply: AuthReply) {
    (reply: @unchecked) match {
      case AuthRecieverReply(response, _, toEchoId) =>
        println(requestor);
        node ! AuthSenderReply(id, toEchoId)
        requestor ! response
    }
  }
}

private[protocol] class ReceiverAuthActor(kBucketActor: ActorRef, requestHandler: ActorRef, selfNode: ActorRef) extends AuthActor(kBucketActor) {
  var toEchoBack: Int = 0

  override protected def authChallenge(msg: Message) = msg match {
    case AuthSenderRequest(req, echoId) =>
      toEchoBack = echoId
      requestHandler ! req
    case reply: Reply =>
      requestor.tell(AuthRecieverReply(reply, toEchoBack, toEchoId), selfNode) // send the reply back
  }
}

object AuthActor {
  trait Provider {
	  def authSender(id: Id, kBucketActor: ActorRef, node: ActorRef): Actor =  new SenderAuthActor(id, kBucketActor, node)
  }
}


