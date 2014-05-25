package com.tommo.kademlia.protocol

import scala.util.Random
import scala.reflect.ClassTag
import scala.concurrent.duration.Duration
import akka.actor.{ Actor, ActorRef, ActorLogging, ReceiveTimeout }

import Message._
import RequestSenderActor._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.routing.KBucketSetActor.Add

private[protocol] abstract class AuthActor[V](kBucketActor: ActorRef, timeout: Duration)(implicit cta: ClassTag[V]) extends Actor with ActorLogging {
  val toEchoId = Random.nextInt

  var requestor = context.system.deadLetters
  var init = false

  final def receive = initAuthChallenge orElse doAfterChallenge orElse authCheck orElse timeOutMsg orElse unknownMsg

  private def initAuthChallenge: Receive =  {
    case v if !init && cta.runtimeClass.isInstance(v) =>
      init = true
      requestor = sender
      doInChallenge(v.asInstanceOf[V])
  }

  def doInChallenge(msg: V) = {}

  def doAfterChallenge(): Receive = Actor.emptyBehavior

  private def authCheck: Receive = {
    case reply: AuthReply if reply.echoId == toEchoId =>
      authSuccess(reply)
      if (addToKBucket) kBucketActor ! Add(ActorNode(sender, reply.sender))
      finish()
  }

  val addToKBucket = true
  
  def authSuccess(reply: AuthReply): Unit

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

private[protocol] class SenderAuthActor(val selfId: Id, kBucketActor: ActorRef, node: ActorRef, discoverNewNode: Boolean, customData: Option[Any], timeout: Duration, selfNode: ActorRef) extends AuthActor[Request](kBucketActor, timeout) {
  var request =  Option.empty[Request]

  override val addToKBucket = discoverNewNode

  override def doInChallenge(req: Request) {
      request = Some(req)
      enableTimeout()
      node.tell(AuthSenderRequest(req, toEchoId), selfNode)
  }

  override def doTimeOut() {
    requestor ! RequestTimeout(request.get, customData)
    super.doTimeOut()
  }

  override def authSuccess(reply: AuthReply) {
    (reply: @unchecked) match {
      case AuthRecieverReply(response, _, toEchoId, _) =>
        node.tell(AuthSenderReply(selfId, toEchoId), selfNode)

        customData match {
          case Some(customData) => requestor ! CustomReply(response, customData)
          case None => requestor ! response
        }
    }
  }
}

private[protocol] class ReceiverAuthActor(selfId: Id, kBucketActor: ActorRef, requestHandler: ActorRef, selfNode: ActorRef, timeout: Duration) extends AuthActor[AuthSenderRequest](kBucketActor, timeout) {
  var toEchoBack = 0
  var mutRequest = Option.empty[MutableRequest]

  override def doAfterChallenge: Actor.Receive = {
    case reply: Reply =>
      sendBackReply(reply)
  }

  override def doInChallenge(authReq: AuthSenderRequest) {
      toEchoBack = authReq.toEcho
      
      val request = authReq.req

      request match {
        case mutable: MutableRequest =>
          sendBackReply(AckReply)
          mutRequest = Some(mutable)
        case _ => requestHandler ! request
      }
  }

  private def sendBackReply(reply: Reply) {
    requestor.tell(AuthRecieverReply(reply, toEchoBack, toEchoId, selfId), selfNode)
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
    def authSender(selfId: Id, kBucketActor: ActorRef, node: ActorRef, discoverNewNode: Boolean, customData: Option[Any],
      timeout: Duration, selfNode: ActorRef): Actor = new SenderAuthActor(selfId, kBucketActor, node, discoverNewNode, customData, timeout, selfNode)
  }
}
