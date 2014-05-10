package com.tommo.kademlia

import com.tommo.kademlia.identity.{ Id, IdGenerator }
import akka.actor.Actor
import org.mockito.Matchers._
import org.mockito.Mockito._

class KadAkkaTest extends BaseTestKit("KadAkkaTest") with BaseFixture {
  test("invoke IdGenerator to get a self generated id") {
    trait MockIdGen extends IdGenerator {
      lazy val mockId = mock[MockIdGen]
      override def generateId(addressSpace: Int) = mockId.generateId(addressSpace)
    }

    val kadAkka = new KadAkka with MockIdGen with KadActorProvider {
      verify(mockId).generateId(mockConfig.addressSpace)
    }
  }

  test("send the actor a join msg when constructed with an existing kad network") {
    trait MockProvider extends KadActorProvider {
      override def newKadActor(self: Id)(implicit config: KadConfig) = { wrapTestActor }
    }

    new ExistingKadNetwork(mockHost, system) with IdGenerator with MockProvider
    
    expectMsg("Joining")
  }
}