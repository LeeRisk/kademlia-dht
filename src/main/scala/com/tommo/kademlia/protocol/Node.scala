package com.tommo.kademlia.protocol

import com.tommo.kademlia.identity.Id
import akka.actor.ActorRef

sealed abstract class Node {
  def id: Id
}

case class RemoteNode(host: Host, id:Id) extends Node

case class ActorNode(ref: ActorRef, id:Id) extends Node

