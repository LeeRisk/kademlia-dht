package com.tommo.kademlia

import akka.actor.{Actor, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit }

import org.scalatest._


abstract class BaseTestKit(name: String) extends TestKit(ActorSystem(name)) with ImplicitSender with BaseUnitTest with BeforeAndAfterAll {
  def wrapTestActor() = new Actor {
    def receive = {
      case msg => testActor forward msg
    }
  }

  override def afterAll() {
    system.shutdown()
  }
}