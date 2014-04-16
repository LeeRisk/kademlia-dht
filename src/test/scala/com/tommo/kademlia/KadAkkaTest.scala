package com.tommo.kademlia

import com.tommo.kademlia.identity.{Id, IdGenerator}
import akka.actor.Actor
class KadAkkaTest extends BaseTestKit("KadAkkaTest") {
  
  trait MockProvider extends KadActorProvider {
    override def newKadActor(self: Id)(implicit config: KadConfig): Actor = wrapTestActor
  }

  "KadAkka" should "invoke IdGenerator to get a self generated id" in {
    trait MockIdGen extends IdGenerator {
      override def generateId(addressSpace: Int) = {
        assert(addressSpace == mockConfig.addressSpace)
        mockZeroId(addressSpace)
      }
    }

    val kadAkka = new KadAkka with MockIdGen with KadActorProvider
  }
  
  it should "send the actor a join msg when constructed with an existing kad network" in {
    val kadAkka = new ExistingKadNetwork(mockHost, system) with IdGenerator with MockProvider
    expectMsg("Joining")
  }
}