package com.tommo.kademlia.protocol


import akka.actor.{ Actor, ActorRef, Props }
import scala.concurrent.duration.Duration
import com.tommo.kademlia.identity.Id

class RequestSenderActor(selfId: Id, kBucketActor: ActorRef, timeout: Duration, selfNode: ActorRef) extends Actor {
  this: AuthActor.Provider =>

  import RequestSenderActor._

  def receive = {
    case NodeRequest(node, req, discoverNewNode, customData) =>
      val autoRef = context.actorOf(Props(authSender(selfId, kBucketActor, node, discoverNewNode, customData, timeout, selfNode)))
      autoRef forward req
  }

}
object RequestSenderActor {
  implicit def customDataToSome(any: Any) = Some(any)
  
  case class NodeRequest(node: ActorRef, request: Request, discoverNewNode: Boolean = true, customData: Option[Any] = None)
  case class CustomReply(val reply: Reply, val customData: Any)
  case class RequestTimeout(request: Request, customData: Option[Any] = None)
}