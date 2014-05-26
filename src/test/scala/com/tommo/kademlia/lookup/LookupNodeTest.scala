package com.tommo.kademlia.lookup

import LookupNode._
import akka.testkit.{ TestFSMRef, TestProbe }
import akka.actor.FSM._
import scala.collection.immutable.TreeMap
import scala.concurrent.duration._
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.RequestSenderActor._
import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.BaseFixture
import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.protocol.ActorNode

class LookupNodeTest extends BaseTestKit("LookupNodeSpec") with BaseFixture {

  class Fixture extends LookupFixture(LookupNodeTest.this) {
    import mockConfig._
    val mockTimeOut = mockConfig.roundTimeOut
    
    lazy val ref = TestFSMRef(new LookupNode(ActorNode(TestProbe().ref, id), kClosestProbe.ref, reqSendProbe.ref, kBucketSize, roundConcurrency, mockTimeOut))

    lazy val underlyingFsm = ref.underlyingActor
  }

  test("start in Initial state with Empty data") {
    new Fixture {
      ref.stateName should equal(Initial)
      ref.stateData should equal(Empty)
    }
  }

  test("send to the KCloseset Actor the id to get the k-closest locally known nodes") {
    new Fixture {
      ref ! FindKClosest(toFindId)
      kClosestProbe.expectMsg(KClosestRequest(toFindId, mockConfig.kBucketSize))

      ref.stateName should equal(WaitForLocalKclosest)
      ref.stateData should equal(lookupReq)
    }
  }

  test("go to QueryNode state when the k-closest locally known nodes is received from the KCloseset Actor") {
    new Fixture {
      ref.setState(WaitForLocalKclosest, lookupReq)

      ref ! KClosestReply(id, mockActorNode("1010") :: Nil)

      ref.stateName should equal(QueryNode)
    }
  }

  test("query α concurrently for each round") {
    new Fixture {
      ref.setState(WaitForLocalKclosest, lookupReq)
      ref.setState(QueryNode, queryNodeDataDefault())

      reqSendProbe.expectMsgAllOf(NodeRequest(testActor, KClosestRequest(toFindId, mockConfig.kBucketSize)),
        NodeRequest(testActor, KClosestRequest(toFindId, mockConfig.kBucketSize)))
    }
  }

  test("if remote node returns result add it and the newly discovered result to seen") {
    new Fixture {
      val data = queryNodeDataDefault()

      ref.setState(QueryNode, data)
      ref.cancelTimer("startQueryNode")

      val (remoteId, remoteState) = data.toQuery.head

      val anId = "0001"

      ref ! Start
      ref ! KClosestReply(remoteId, List(mockActorNode(anId)))

      inside(ref.stateData) {
        case QueryNodeData(_, _, seen, querying, _, _) =>
          querying shouldBe data.toQuery - remoteId
          seen shouldBe data.seen + mockActorNodePair(anId, round = 2) + (remoteId -> remoteState.copy(respond = true))
      }
    }
  }

  test("go from QueryNode to GatherNode after the specified round timeout") {
    new Fixture {

      override val mockTimeOut = 10 millis

      val data = queryNodeDataDefault()

      ref.setState(WaitForLocalKclosest, lookupReq)
      ref.setState(QueryNode, data)

      awaitAssert(ref.stateName should equal(GatherNode), max = (100 millis) + mockConfig.roundTimeOut, interval = 100 micro)
    }
  }

  test("continue querying up to α unqueried nodes if there is a newly discovered node that is closer than the previous") {
    new Fixture {
      val (c1Responded, newEvenCloser) = (mockActorNodePair("0010", respond = true), mockActorNodePair("0001", round = nextRound))

      val data = QueryNodeData(lookupReq, seen = TreeMap(c1Responded, newEvenCloser), currRound = round)

      ref.setState(GatherNode, data)
      ref.cancelTimer("startGatherNode")

      ref ! Start

      ref.stateName should equal(QueryNode)

      inside(ref.stateData) {
        case QueryNodeData(_, toQuery, _, _, nextRound, _) =>
          toQuery shouldBe Map(newEvenCloser)
          nextRound shouldBe this.nextRound
      }
    }
  }

  test("query all unqueried k-closest node if no new closer node has been discovered") {
    new Fixture {
      val defaultData = queryNodeDataDefault(0)

      ref.setState(GatherNode, defaultData)
      ref.cancelTimer("startGatherNode")
      ref ! Start

      ref.stateName should equal(QueryNode)

      inside(ref.stateData) {
        case QueryNodeData(_, toQuery, _, _, _, lastRound) =>
          lastRound shouldBe true
          toQuery shouldBe defaultData.seen.take(mockConfig.kBucketSize)
      }
    }
  }

  test("send the k-closest back to sender as list") {
    new Fixture {
      val result = FinalizeData(lookupReq, ActorNode(testActor, aRandomId) :: ActorNode(testActor, aRandomId) :: Nil)

      ref.setState(Finalize, result)
      ref ! Start

      expectMsg(Result(lookupReq.id, result.kClosest))
    }
  }
}