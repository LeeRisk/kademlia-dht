package com.tommo.kademlia.protocol

import com.tommo.kademlia.identity.Id

import akka.actor.{ Actor, ActorRef, Props }

class SenderActor(id: Id, kBucketActor: ActorRef) extends Actor {
  this: AuthActor.Provider =>

  import SenderActor._

  def receive = {
    case NodeRequest(node, req) => 
      val autoRef = context.actorOf(Props(authSender(id, kBucketActor, node)))
      autoRef forward req
  }

}
object SenderActor {
  case class NodeRequest(node: ActorRef, request: Request)

}