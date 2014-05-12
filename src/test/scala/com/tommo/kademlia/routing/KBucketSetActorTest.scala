package com.tommo.kademlia.routing

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.protocol.RequestSenderActor._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.protocol.Node
import akka.actor.{ Props, Actor, ActorRef }
import akka.testkit.{ TestActorRef, TestProbe, TestActor }
import org.mockito.Matchers._
import org.mockito.Mockito._

class KBucketSetActorTest extends BaseTestKit("KBucketSpec") with BaseKBucketFixture {

  trait Fixture extends BaseKBucketFixture {

    val kSet = mock[KBucketSet[ActorNode]]

    trait MockProvider extends KBucketSet.Provider {
      override def newKSet[T <: Node](id: Id, kBucketCapacity: Int) = kSet.asInstanceOf[KBucketSet[T]]
    }

    val mockProbe = TestProbe()
    val reqSendProbe = TestProbe()

    val kClosest = ActorNode(mockProbe.ref, aRandomId) :: ActorNode(mockProbe.ref, aRandomId) :: Nil

    when(kSet.getClosestInOrder(anyInt(), any())).thenReturn(kClosest)

    val verifyRef = TestActorRef[KBucketSetActor](Props(new KBucketSetActor(reqSendProbe.ref) with MockProvider))

  }

  test("return the KClosest to an id by invoking getClosestInOrder method") {
    new Fixture {
      verifyRef ! KClosestRequest(mockConfig.id, mockZeroId(4), mockConfig.kBucketSize)

      expectMsg(KClosestReply(mockConfig.id, kClosest))

      verify(kSet).getClosestInOrder(mockConfig.kBucketSize, mockZeroId(4))
    }
  }

  test("add node if the kBucket is not full") {
    new Fixture {
      val actorNode = ActorNode(mockProbe.ref, aRandomId)
      verifyRef ! Add(actorNode)
      verify(kSet).add(actorNode)
    }
  }

  test("don't add if kBucket is full and the lowest ordered node is still alive") {
    new Fixture {
      val lowest = ActorNode(mockProbe.ref, aRandomId)
      val toAdd = ActorNode(mockProbe.ref, aRandomId)

      when(kSet.isFull(any())).thenReturn(true)
      when(kSet.getLowestOrder(any())).thenReturn(lowest)

      reqSendProbe.setAutoPilot(new TestActor.AutoPilot {
        def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = msg match {
          case NodeRequest(lowest.ref, PingRequest(lowest.id), _, _) =>
            sender ! AckReply(lowest.id); TestActor.NoAutoPilot
          case _ => TestActor.NoAutoPilot
        }
      })

      verifyRef ! Add(toAdd)

      reqSendProbe.expectMsg(NodeRequest(lowest.ref, PingRequest(lowest.id), customData = (lowest, toAdd)))

      verify(kSet, never()).add(any())
    }
  }

  test("add if kBucket is full and the lowest ordered node is dead") {
    new Fixture {
      val lowest = ActorNode(mockProbe.ref, aRandomId)

      when(kSet.isFull(any())).thenReturn(true)
      when(kSet.contains(lowest)).thenReturn(true)
      when(kSet.getLowestOrder(any())).thenReturn(lowest)

      reqSendProbe.setAutoPilot(new TestActor.AutoPilot {
        def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = msg match {
          case NodeRequest(lowest.ref, PingRequest(lowest.id), _, _) =>
            sender ! RequestTimeout(PingRequest(lowest.id), customData = (lowest, toAdd)); 
            TestActor.NoAutoPilot
          case _ => TestActor.NoAutoPilot
        }
      })

      val toAdd = ActorNode(mockProbe.ref, aRandomId)

      verifyRef ! Add(toAdd)

      reqSendProbe.expectMsg(NodeRequest(lowest.ref, PingRequest(lowest.id),  customData = (lowest, toAdd)))

      awaitAssert(verify(kSet).add(toAdd))
    }
  }

  test("return number of kBuckets") {
    new Fixture {
      when(kSet.addressSize).thenReturn(5)

      verifyRef ! GetNumKBuckets

      expectMsg(NumKBuckets(5))

    }
  }

  test("discard to add id if it is same as self") {
    new Fixture {
      verifyRef ! Add(ActorNode(testActor, mockConfig.id))
      verifyZeroInteractions(kSet) // TODO use await 
    }
  }
}