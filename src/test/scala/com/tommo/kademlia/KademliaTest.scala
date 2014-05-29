package com.tommo.kademlia

import com.tommo.kademlia.identity.{ Id, IdGenerator }
import akka.actor.Actor
import akka.testkit.TestProbe
import Kademlia._
import scala.concurrent.duration._
import scala.concurrent.Await
import com.tommo.kademlia.protocol.Host

class KademliaTest extends BaseTestKit("KademliaSpec") with BaseFixture {

  trait KadFixture {
    val selfProbe = TestProbe()

    trait MockProviderWrapper extends KadActorProvider[Int] {
      override def newNode(self: Id)(implicit config: KadConfig): Actor = wrapActorRef(selfProbe.ref)
    }
  }

  def withNewKad(testFn: (Kademlia[Int], KadFixture) => Any) {
    new KadFixture {
      val kad = new Kademlia[Int](id) with MockProviderWrapper
      testFn(kad, this)
      kad.terminate()
    }
  }

  test("start actor system with correct name") {
    withNewKad { (kad, fixture) =>
      kad.actorSystem.name shouldBe mockConfig.name
    }
  }

  test("should start kad node using /user/nodeName") {
    withNewKad { (kad, fixture) =>
      val ref = Await.result(kad.actorSystem.actorSelection(s"/user/$nodeName").resolveOne(5 seconds), 5 seconds)
      ref.path.name shouldBe nodeName
    }
  }

  test("Insert msg when put") {
    withNewKad { (kad, fixture) =>
      import com.tommo.kademlia.store.StoreActor.Insert
      import fixture._

      val anId = aRandomId(id.size)

      kad.put(anId, 3)

      selfProbe.expectMsg(Insert(anId, 3))
    }
  }

  test("LookupValueFSM.FindValue msg when get") {
    withNewKad { (kad, fixture) =>
      import com.tommo.kademlia.lookup.LookupValueFSM.FindValue
      import fixture._

      val anId = aRandomId(id.size)

      kad.get(anId)

      selfProbe.expectMsg(FindValue(anId))
    }
  }

  trait ExistingKadFixture extends KadFixture {
    import akka.actor.{ ActorSystem, ActorSelection }

    val remoteRef = TestProbe().ref

    trait MockExisting extends RemoteSelector {
      override def getSelection(existing: ExistingHost, sys: ActorSystem) = {
        system.actorSelection(remoteRef.path.parent + "/" + remoteRef.path.name)
      }
    }

  }

  def withExistingKad(testFn: (ExistingKademlia[Int], ExistingKadFixture) => Any) {
    new ExistingKadFixture {
      class _Mock extends {
        override val actorSystem = system
      } with ExistingKademlia[Int](ExistingHost("systemName", Host("65.43.3.1", 20)), id) with MockProviderWrapper with MockExisting
      val kad = new _Mock()
      testFn(kad, this)
      kad.terminate()
    }
  }

  test("existing actor path") {
    getPath(ExistingHost("systemName", Host("65.43.3.1", 20))) shouldBe "akka.udp://systemName@65.43.3.1:20/user/kadNode"
  }

  test("send selection ref to self node") {
    withExistingKad { (kad, fixture) =>
      import akka.actor.Identify
      import fixture._
      import scala.concurrent.duration._

      selfProbe.expectMsg(300 seconds, remoteRef)
    }
  }
}