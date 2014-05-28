package com.tommo.kademlia.protocol

import scala.util.Random
import scala.reflect.ClassTag
import scala.concurrent.duration.Duration
import akka.actor.{ Actor, ActorRef, ActorLogging, ReceiveTimeout }

import Message._
import RequestDispatcher._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.routing.KBucketSetActor.Add

private[protocol] abstract class AuthActor[V](kBucketSet: ActorRef, timeout: Duration)(implicit cta: ClassTag[V]) extends Actor with ActorLogging {
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
      if (addToKBucket) kBucketSet ! Add(ActorNode(sender, reply.sender))
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

private[protocol] class SenderAuthActor(val selfNode: ActorNode, kBucket: ActorRef, node: ActorRef, discoverNewNode: Boolean, customData: Option[Any], timeout: Duration) extends AuthActor[Request](kBucket, timeout) {
  var request =  Option.empty[Request]

  override val addToKBucket = discoverNewNode

  override def doInChallenge(req: Request) {
      request = Some(req)
      node.tell(AuthReceiverRequest(req, toEchoId), selfNode.ref)
      enableTimeout()
  }

  override def doTimeOut() {
    requestor ! RequestTimeout(request.get, customData)
    super.doTimeOut()
  }

  override def authSuccess(reply: AuthReply) {
    (reply: @unchecked) match {
      case AuthRecieverReply(response, _, toEchoId, _) =>
        node.tell(AuthSenderReply(selfNode.id, toEchoId), selfNode.ref)

        customData match {
          case Some(customData) => requestor ! CustomReply(response, customData)
          case None => requestor ! response
        }
    }
  }
}

private[protocol] class ReceiverAuthActor(selfNode: ActorNode, kBucket: ActorRef, requestHandler: ActorRef, timeout: Duration) extends AuthActor[AuthReceiverRequest](kBucket, timeout) {
  var toEchoBack = 0
  var mutRequest = Option.empty[MutableRequest]

  override def doAfterChallenge: Actor.Receive = {
    case reply: Reply =>
      sendBackReply(reply)
  }

  override def doInChallenge(authReq: AuthReceiverRequest) {
      toEchoBack = authReq.toEcho
      
      val request = authReq.req

      request match {
        case mutable: MutableRequest =>
          sendBackReply(AckReply)
          mutRequest = Some(mutable)
        case _ => sendRequest(request)
      }
  }
  
  private def sendRequest(req: Request) {
    requestHandler ! req
    enableTimeout()
  }

  private def sendBackReply(reply: Reply) {
    requestor.tell(AuthRecieverReply(reply, toEchoBack, toEchoId, selfNode.id), selfNode.ref)
  }

  override def authSuccess(reply: AuthReply) {
    mutRequest match {
      case Some(mutableReq) => sendRequest(mutableReq)
      case _ =>
    }
  }
}

object AuthActor {
  trait Provider {
    def authReceiver(selfNode: ActorNode, kBucketSet: ActorRef, requestHandler: ActorRef, timeout: Duration): Actor =
      new ReceiverAuthActor(selfNode, kBucketSet, requestHandler, timeout)
    
    def authSender(selfNode: ActorNode, kBucketSet: ActorRef, node: ActorRef, 
        discoverNewNode: Boolean, customData: Option[Any], timeout: Duration): Actor = new SenderAuthActor(selfNode, kBucketSet, node, discoverNewNode, customData, timeout)
  }
}
