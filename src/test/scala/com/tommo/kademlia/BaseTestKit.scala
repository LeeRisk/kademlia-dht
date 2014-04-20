package com.tommo.kademlia

import akka.actor.{Actor, ActorSystem, ActorRef }
import akka.testkit.{ ImplicitSender, TestKit }
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.identity.Id

import org.scalatest._
import org.mockito.Matchers._
import org.mockito.Mockito._

abstract class BaseTestKit(name: String) extends TestKit(ActorSystem(name)) with ImplicitSender with BaseUnitTest with BeforeAndAfterAll {
  def mockActorNode(id: String) = ActorNode(testActor, Id(id))
  
  def wrapTestActor() = wrapActorRef(testActor)
  
  def wrapActorRef(ref: ActorRef) = new Actor {
    def receive = {
      case msg => ref forward msg
    }
  }
  
  override def afterAll() {
    system.shutdown()
  }
}