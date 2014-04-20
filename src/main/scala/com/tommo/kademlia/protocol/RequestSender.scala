package com.tommo.kademlia.protocol

import com.tommo.kademlia.identity.Id

import akka.actor.{ Actor, ActorRef, Props }

class RequestSenderActor(id: Id, kBucketActor: ActorRef) extends Actor {
  this: AuthActor.Provider =>

  import RequestSenderActor._

  def receive = {
    case NodeRequest(node, req) => 
      val autoRef = context.actorOf(Props(authSender(id, kBucketActor, node)))
      autoRef forward req
  }

}
object RequestSenderActor {
  case class NodeRequest(node: ActorRef, request: Request)
}