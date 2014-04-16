package com.tommo.kademlia.protocol

import akka.actor.{ Actor, ActorRef, ActorLogging }
import com.tommo.kademlia.protocol._
import com.tommo.kademlia.routing.KBucketSetActor.Add
import com.tommo.kademlia.identity.Id
import scala.util.Random
import akka.actor.actorRef2Scala

abstract class AuthActor(kBucketActor: ActorRef) extends Actor with ActorLogging {
  val toEchoId = Random.nextInt

  // TODO add timer if response is not received

  final def receive = authChallenge orElse authCheck

  def authChallenge: Actor.Receive
  def authSuccess(reply: AuthReply) {}

  final def authCheck: Actor.Receive = {
    case reply: AuthReply if reply.echoId == toEchoId =>
      authSuccess(reply)

      reply match {
        case AuthRecieverReply(msg, _, _) => kBucketActor ! Add(ActorNode(sender, msg.sender))
        case AutoSenderReply(id, _) => kBucketActor ! Add(ActorNode(sender, id))
      }

      context.stop(self)
    case unknown =>
      log.warning(s"Received unknown message when doing auth check: $unknown")
      context.stop(self)
  }
}

class SenderAuthActor(val id: Id, kBucketActor: ActorRef, node: ActorRef, toForward: ActorRef) extends AuthActor(kBucketActor) {
  def authChallenge = {
    case req: Request => node ! AuthSenderRequest(req, toEchoId)
  }

  override def authSuccess(reply: AuthReply) {
    (reply: @unchecked) match {
      case AuthRecieverReply(response, _, toEchoId) =>
        node ! AutoSenderReply(id, toEchoId)
        toForward ! response
    }
  }
}

class ReceiverAuthActor(kBucketActor: ActorRef, requestHandler: ActorRef, toSendNode: ActorRef) extends AuthActor(kBucketActor) {
  var toEchoBack: Int = 0
  var originalSender = context.system.deadLetters
  
  def authChallenge = {
    case AuthSenderRequest(req, echoId) => 
      requestHandler ! req
      toEchoBack = echoId
      originalSender = sender 
    case reply: Reply => 
      toSendNode.tell(AuthRecieverReply(reply, toEchoBack, toEchoId), originalSender)
  }
}