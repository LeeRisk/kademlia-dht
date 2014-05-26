package com.tommo.kademlia

import com.tommo.kademlia.identity.{ Id, IdGenerator }
import akka.actor.Actor
import akka.testkit.TestProbe
import Kademlia._
import scala.concurrent.duration._
import scala.concurrent.Await

class KademliaTest extends BaseTestKit("KademliaSpec") with BaseFixture {

  trait Fixture {
    val newProbe = TestProbe()
    val existingProbe = TestProbe()

    trait MockProviderWrapper extends KadActorProvider {
      override def newNetwork(self: Id)(implicit config: KadConfig): Actor = wrapActorRef(newProbe.ref)
      override def joinNetwork(self: Id, existing: ExistingHost)(implicit config: KadConfig): Actor = wrapActorRef(existingProbe.ref)
    }

    val kad = new Kademlia(id) with MockProviderWrapper
  }

  test("start actor system with correct name") {
    new Fixture {
      kad.actorSystem.name shouldBe mockConfig.name
    }
  }
  
  test("should start kad node using /user/nodeName") {
    new Fixture {
      val ref = Await.result(kad.actorSystem.actorSelection(s"/user/$nodeName").resolveOne(5 seconds), 5 seconds)
      ref.path.name shouldBe nodeName
    }
  }
}