package com.tommo.kademlia.protocol


import akka.actor.{ Actor, ActorRef, Props }
import scala.concurrent.duration.Duration
import com.tommo.kademlia.identity.Id
import Message._

class RequestDispatcher(selfNode: ActorNode, kSet: ActorRef, reqHandlerRef: ActorRef, timeout: Duration) extends Actor {
  this: AuthActor.Provider =>

  import RequestDispatcher._

  def receive = {
    case NodeRequest(node, req, discoverNewNode, customData) => context.actorOf(Props(authSender(selfNode, kSet, node, discoverNewNode, customData, timeout))) forward req
    case authRequest: AuthSenderRequest => context.actorOf(Props(authReceiver(selfNode, kSet, reqHandlerRef, timeout))) forward authRequest
  }
}

object RequestDispatcher {
  trait Provider {
    def newRequestDispatcher(selfNode: ActorNode, kSet: ActorRef, reqHandlerRef: ActorRef, timeout: Duration): Actor = 
      new RequestDispatcher(selfNode, kSet, reqHandlerRef, timeout) with AuthActor.Provider
  }
  
  implicit def customDataToSome(any: Any) = Some(any)
  
  case class NodeRequest(node: ActorRef, request: Request, discoverNewNode: Boolean = true, customData: Option[Any] = None)
  case class RequestTimeout(request: Request, customData: Option[Any] = None)
  case class CustomReply(val reply: Reply, val customData: Any)
  
}