package com.tommo.kademlia.protocol

import com.tommo.kademlia.identity.Id
import akka.actor.ActorRef

abstract class Message { def sender: Id }

trait Request extends Message
trait Reply extends Message

/* Piggyback on request/response to verify authenticity of sender/receiver */
sealed trait AuthChallenge extends Message { val toEcho: Int }
sealed abstract class AuthReply extends Message { val echoId: Int }

case class AuthSenderRequest(req: Request, val toEcho: Int) extends AuthChallenge { val sender = req.sender }
case class AuthRecieverReply(reply: Reply, echoId: Int, toEcho: Int) extends AuthReply with AuthChallenge { val sender = reply.sender }
case class AuthSenderReply(sender: Id, echoId: Int) extends AuthReply

object Message {
  case class PingRequest(val sender: Id) extends Request
  case class PingReply(val sender: Id) extends Reply

  case class KClosestRequest(val sender: Id, searchId: Id, k: Int) extends Request
  case class KClosestReply(val sender: Id, nodes: List[ActorNode]) extends Reply
}

