package com.tommo.kademlia.protocol

import com.tommo.kademlia.identity.Id
import akka.actor.ActorRef

abstract class Message { def sender: Id }

trait Request extends Message 
trait Reply extends Message

case class PingRequest(val sender: Id) extends Request
case class PingReply(val sender: Id) extends Reply

case class KClosestRequest(val sender: Id, searchId: Id) extends Request
case class KClosestReply(val sender: Id, nodes: List[ActorNode]) extends Reply

/* Piggyback on request/response to verify authenticity of sender/receiver */
trait AuthChallenge { val toEcho: Int }
sealed abstract class AuthReply { val echoId: Int } 
case class AuthSenderRequest(req: Request, val toEcho: Int) extends AuthChallenge
case class AuthRecieverReply(responseMsg: Reply, echoId: Int, toEcho: Int) extends AuthReply with AuthChallenge
case class AutoSenderReply(sender: Id, echoId: Int) extends AuthReply

