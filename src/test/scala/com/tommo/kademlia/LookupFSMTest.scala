package com.tommo.kademlia

import LookupFSM._

import akka.testkit.{ TestFSMRef, TestProbe }
import akka.actor.{ ActorRef, Actor }
import akka.actor.FSM._

import scala.collection.immutable.TreeMap
import scala.concurrent.duration._

import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.RequestSenderActor._
import com.tommo.kademlia.routing.KBucketSetActor._

class LookupFSMTest extends BaseTestKit("LookupFSMSpec") with BaseFixture {

  trait Fixture {
    val kBucketActor = testActor
    val reqSendProbe = TestProbe()
    val config = mockConfig

    import config._

    val roundTimeout = config.roundTimeOut

    lazy val ref = TestFSMRef(new LookupFSM(id, kBucketActor, reqSendProbe.ref, kBucketSize, roundConcurrency, roundTimeout))
    lazy val underlyingFsm = ref.underlyingActor

    val toFindId = mockZeroId(4)
    val lookupReq = Lookup(toFindId, testActor)

    implicit val order = new lookupReq.id.SelfOrder

    def transform(pair: (Id, NodeQuery), tFn: NodeQuery => NodeQuery) = (pair._1, tFn(pair._2))

    val nodeQueryDefaults = QueryNodeData(req = lookupReq, seen = TreeMap())
    val round = nodeQueryDefaults.currRound
    val nextRound = round + 1

    def queryNodeDataDefault(toQuery: Int = config.roundConcurrency) = takeAndUpdate(nodeQueryDefaults.copy(seen = mockSeen), toQuery)

    lazy val mockSeen = TreeMap(mockActorNodePair("1000"), mockActorNodePair("1001"), mockActorNodePair("1011"), mockActorNodePair("1101"))

    def mockActorNodePair(id: String, round: Int = round, respond: Boolean = false) = actorNodeToKeyPair(mockActorNode(id), round, respond)
  }

  test("start in Initial state with Empty data") {
    new Fixture {
      ref.stateName should equal(Initial)
      ref.stateData should equal(Empty)
    }
  }

  test("send to the KBucket Actor the id to get the k-closest locally known nodes") {
    new Fixture {
      ref ! toFindId
      expectMsg(GetKClosest(toFindId, mockConfig.kBucketSize))

      ref.stateName should equal(QueryKBucket)
      ref.stateData should equal(QueryKBucketData(lookupReq))
    }
  }

  test("go to QueryNode state when the k-closest locally known nodes is received from the KBucket Actor") {
    new Fixture {
      ref.setState(QueryKBucket, QueryKBucketData(lookupReq))

      ref ! KClosest(toFindId, mockActorNode("1010") :: Nil)

      ref.stateName should equal(QueryNode)
    }
  }

  test("query α concurrently for each round") {
    new Fixture {
      ref.setState(QueryKBucket, QueryKBucketData(lookupReq))
      ref.setState(QueryNode, queryNodeDataDefault())

      reqSendProbe.expectMsgAllOf(NodeRequest(testActor, KClosestRequest(mockConfig.id, toFindId, config.kBucketSize)),
        NodeRequest(testActor, KClosestRequest(mockConfig.id, toFindId, config.kBucketSize)))
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

      override val roundTimeout = 10 millis

      val data = queryNodeDataDefault()

      ref.setState(QueryKBucket, QueryKBucketData(lookupReq))
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
        val result = Result(lookupReq, ActorNode(testActor, aRandomId) :: ActorNode(testActor, aRandomId) :: Nil)
  
        ref.setState(Finalize, result)
        ref ! Start
        
        expectMsg(result.kClosest)
        
      }
    }
}