package com.tommo.kademlia.lookup

import akka.testkit.{ TestFSMRef, TestProbe }
import akka.actor.FSM._
import scala.collection.immutable.TreeMap

import LookupFSM._
import LookupValue._
import com.tommo.kademlia.BaseFixture
import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.protocol.Message.{ FindValueReply, FindValueRequest, StoreRequest }
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.protocol.RequestSenderActor._
import com.tommo.kademlia.identity.Id

class LookupValueTest extends BaseTestKit("LookupValueSpec") with BaseFixture {
  class Fixture extends LookupFixture(LookupValueTest.this) {
    import mockConfig._

    val someRef = TestProbe().ref

    val ref = TestFSMRef(new LookupValue[Int](id, kClosestProbe.ref, reqSendProbe.ref, kBucketSize, roundConcurrency, roundTimeOut))
    val listOfNodes = ActorNode(someRef, aRandomId(kBucketSize)) :: ActorNode(someRef, aRandomId(kBucketSize)) :: Nil
  }

  test("return FindValueRequest") {
    new Fixture {
      ref.underlyingActor.getRequest(mockZeroId(4), 4) shouldBe FindValueRequest(mockConfig.id, mockZeroId(4), 4)
    }
  }
  
  test("if local store returns values go to finalize") {
    new Fixture {
      ref.setState(WaitForLocalKclosest, lookupReq)
      ref ! FindValueReply(mockZeroId(4), Right(Set(1, 2, 3)))
      awaitAssert(ref.stateName shouldBe FinalizeValue)
    }    
  }
  
  test("return result as Left") {
    new Fixture {
      ref.underlyingActor.returnResultsAs(listOfNodes) shouldBe Left(listOfNodes)
    }
  }

  test("if a value reply is received and it is a value goto FinalizeValue") {
    new Fixture {
      val qd = queryNodeDataDefault(2)

      ref.setState(QueryNode, qd)

      ref ! FindValueReply(qd.toQuery.head._1, Right(Set(1, 2, 3)))

      awaitAssert(ref.stateName shouldBe FinalizeValue)
    }
  }

  test("if a value reply is received make store request to closest node that did not return the value") {
    new Fixture {
      val lookReq = Lookup(Id("1111"), testActor)
      val (s1, s2) = ((Id("1101"), NodeQuery(TestProbe().ref, 0 , true)), (Id("1100"), NodeQuery(TestProbe().ref, 0 , true)))
      val responded = (Id("0001"), NodeQuery(TestProbe().ref, 0 , false))
      
      val qd = QueryNodeData(lookReq, Map(), TreeMap(s1, s2)(new lookReq.id.SelfOrder), Map(responded))
      ref.setState(QueryNode, qd)

      ref ! FindValueReply(responded._1, Right(Set(1, 2, 3)))

      reqSendProbe.expectMsg(NodeRequest(s1._2.ref, StoreRequest(mockZeroId(4), qd.req.id, Set(1, 2, 3))))
    }
  }

  test("if a reply is received and it is a kclosest go to kClosestState()") {
    new Fixture {
      import mockConfig._

      var invoked = false

      override val ref = TestFSMRef(new LookupValue[Int](id, kClosestProbe.ref, reqSendProbe.ref, kBucketSize, roundConcurrency, roundTimeOut) {
        override def kclosestState(reply: { val nodes: List[ActorNode]; val senderId: Id }, qd: QueryNodeData): State = { invoked = true; super.kclosestState(reply, qd) }
      })

      val qd = queryNodeDataDefault(1)
      ref.setState(QueryNode, qd)
      ref.cancelTimer("startQueryNode")

      ref ! Start

      ref ! FindValueReply(qd.toQuery.head._1, Left(listOfNodes))

      awaitAssert(invoked shouldBe true)
    }
  }

  test("when FinalizeValue return as Right(values)") {
    new Fixture {
      val result = ResultValue(lookupReq, Set(1, 2, 3))
      ref.setState(FinalizeValue, result)

      ref ! Start

      expectMsg(Right(result.values))
    }
  }
}