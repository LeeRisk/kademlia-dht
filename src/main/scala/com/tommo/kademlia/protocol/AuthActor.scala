package com.tommo.kademlia.protocol

import scala.util.Random
import scala.concurrent.duration.Duration
import akka.actor.{ Actor, ActorRef, ActorLogging, ReceiveTimeout }

import Message._
import RequestSenderActor._
import com.tommo.kademlia.routing.KBucketSetActor.Add
import com.tommo.kademlia.identity.Id

private[protocol] abstract class AuthActor(kBucketActor: ActorRef, timeout: Duration) extends Actor with ActorLogging {
  val toEchoId = Random.nextInt

  var requestor = context.system.deadLetters
  var init = false

  final def receive = initAuthChallenge orElse doAfterChallenge orElse authCheck orElse timeOutMsg orElse unknownMsg

  private def initAuthChallenge: Receive = {
    case a: Message if !init =>
      init = true
      requestor = sender
      doInChallenge(a)
  }

  def doInChallenge(msg: Message) = {}

  def doAfterChallenge(): Receive = Actor.emptyBehavior

  private def authCheck: Receive = {
    case reply: AuthReply if reply.echoId == toEchoId =>
      authSuccess(reply)
      if (addToKBucket) kBucketActor ! Add(ActorNode(sender, reply.sender))
      finish()
  }

  val addToKBucket = true
  def authSuccess(reply: AuthReply) {}

  private def timeOutMsg: Receive = {
    case ReceiveTimeout => doTimeOut()
  }

  def doTimeOut() { finish() }

  private def unknownMsg: Receive = {
    case unknown =>
      log.warning(s"Received unknown message when doing auth check: $unknown")
      finish()
  }

  private def finish() {
    context.stop(self)
  }

  def enableTimeout() { context.setReceiveTimeout(timeout) }
  def disableTimeout() { context.setReceiveTimeout(Duration.Undefined) }

}

private[protocol] class SenderAuthActor(kBucketActor: ActorRef, node: ActorRef, discoverNewNode: Boolean, customData: Option[Any], timeout: Duration) extends AuthActor(kBucketActor, timeout) {
  var id: Id = null
  var request: Request = null

  override val addToKBucket = discoverNewNode

  override def doInChallenge(msg: Message) = msg match {
    case req: Request =>
      id = req.sender
      request = req
      enableTimeout()
      node ! AuthSenderRequest(req, toEchoId)
  }

  override def doTimeOut() {
    requestor ! RequestTimeout(request, customData)
    super.doTimeOut()
  }

  override def authSuccess(reply: AuthReply) {
    (reply: @unchecked) match {
      case AuthRecieverReply(response, _, toEchoId) =>
        node ! AuthSenderReply(id, toEchoId)

        customData match {
          case Some(customData) => requestor ! CustomReply(response, customData)
          case None => requestor ! response
        }
    }
  }
}

private[protocol] class ReceiverAuthActor(id: Id, kBucketActor: ActorRef, requestHandler: ActorRef, selfNode: ActorRef, timeout: Duration) extends AuthActor(kBucketActor, timeout) {
  var toEchoBack = 0
  var mutRequest = Option.empty[MutableRequest]

  override def doAfterChallenge: Actor.Receive = {
    case reply: Reply =>
      sendBackReply(reply)
  }

  override def doInChallenge(msg: Message) = msg match {
    case AuthSenderRequest(req, echoId) =>
      toEchoBack = echoId

      req match {
        case mutable: MutableRequest =>
          sendBackReply(AckReply(id))
          mutRequest = Some(mutable)
        case _ => requestHandler ! req
      }
  }

  private def sendBackReply(reply: Reply) {
    requestor.tell(AuthRecieverReply(reply, toEchoBack, toEchoId), selfNode)
  }

  override def authSuccess(reply: AuthReply) {
    mutRequest match {
      case Some(mutableReq) => requestHandler ! mutableReq
      case _ =>
    }
  }
}

object AuthActor {
  trait Provider {
    def authSender(kBucketActor: ActorRef, node: ActorRef, discoverNewNode: Boolean, customData: Option[Any], timeout: Duration): Actor = new SenderAuthActor(kBucketActor, node, discoverNewNode, customData, timeout)
  }
}
