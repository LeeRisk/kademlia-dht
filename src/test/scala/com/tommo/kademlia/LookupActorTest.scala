package com.tommo.kademlia

import LookupActor._

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

class LookupActorTest extends BaseTestKit("LookupSpec") with BaseFixture {
  
  trait Fixture {
    val kBucketActor = testActor
    val reqSendProbe = TestProbe()
    val config = mockConfig

    val ref = TestFSMRef(new LookupActor(kBucketActor, reqSendProbe.ref)(config))
    val underlyingFsm = ref.underlyingActor
    val toFindId = mockZeroId(4)
    val lookupReq = Lookup(toFindId, testActor)
    
    implicit val order = (new lookupReq.id.Order).reverse

    def transform(pair: (Id, NodeQuery), tFn: NodeQuery => NodeQuery) = (pair._1, tFn(pair._2))

    val nodeQueryDefaults = QueryNodeData(req = lookupReq, seen = TreeMap())
    val round = nodeQueryDefaults.currRound
    val nextRound = round + 1

    def queryNodeDataDefault(toQuery: Int = config.concurrency) = QueryNodeData(lookupReq, mockNodeMap.takeRight(toQuery), mockNodeMap)

    lazy val mockNodeMap = TreeMap(mockActorNodePair("1000"), mockActorNodePair("1001"), mockActorNodePair("1011"), mockActorNodePair("1101"))

    def mockActorNodePair(id: String, round: Int = round, queried: Boolean = false) = actorNodeToKeyPair(mockActorNode(id), queried, round)
  }

  test("start in Initial state with Empty data") {
    new Fixture {
      ref.stateName should equal(LookupActor.Initial)
      ref.stateData should equal(LookupActor.Empty)
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

  test("add newly discovered nodes that is returned from a queried node and update the node's status to queried") {
    new Fixture {
      val data = queryNodeDataDefault()

      ref.setState(QueryNode, data)

      val (queryId, queryState) = data.querying.head

      val anId = "0001"
      ref ! KClosestReply(queryId, List(mockActorNode(anId)))

      inside(ref.stateData) {
        case QueryNodeData(_, querying, seen, _, _, _) =>
          querying shouldBe data.querying + (queryId -> queryState.copy(queried = true))
          seen shouldBe data.seen + mockActorNodePair(anId, round = 2)
      }
    }
  }

  test("go from QueryNode to GatherNode after the specified config timeout") {
    new Fixture {
      val data = queryNodeDataDefault()

      underlyingFsm onTransition {
        case QueryNode -> GatherNode =>
          inside(underlyingFsm.nextStateData) {
            case QueryNodeData(_, _, _, responseCount, currRound, _) =>
              if (currRound == round) responseCount shouldBe 2
          }
      }

      ref.setState(QueryKBucket, QueryKBucketData(lookupReq))
      ref.setState(QueryNode, data)

      awaitAssert(ref.stateName should equal(GatherNode), max = 600 millis, interval = 100 micro)
    }
  }

  test("update all successfully queried nodes that are in the seen map") {
    new Fixture {
      val defaultData = queryNodeDataDefault()
      val closestQueryingUpdated = transform(defaultData.querying.head, _.copy(queried = true))
      val data = defaultData.copy(querying = defaultData.querying + closestQueryingUpdated)

      ref.setState(GatherNode, data)
      ref ! Start

      inside(ref.stateData) { case QueryNodeData(_, _, seen, _, _, _) => seen shouldBe data.seen + closestQueryingUpdated }
    }
  }
  test("continue querying up to α unqueried nodes if there is a newly discovered node that is closer than the previous") {
    new Fixture {
      val (c1Queried, c2EvenCloser) = (mockActorNodePair("0010", queried = true), mockActorNodePair("0001", round = nextRound))

      val data = QueryNodeData(lookupReq, seen = TreeMap(c1Queried, c2EvenCloser), responseCount = 0, currRound = round)

      ref.setState(GatherNode, data)
      ref ! Start

      ref.stateName should equal(QueryNode)

      inside(ref.stateData) {
        case QueryNodeData(_, querying, _, responseCount, nextRound, _) =>
          querying shouldBe Map(c2EvenCloser)
          responseCount shouldBe 0
          nextRound shouldBe this.nextRound
      }
    }
  }

  test("query nodes that failed to respond in previous round in addition to the newly discovered ones") {
    new Fixture {
      val (c1, c2, failedNode, successNode) = (mockActorNodePair("0001", round = nextRound), mockActorNodePair("0010", round = nextRound),
        mockActorNodePair("1000"), mockActorNodePair("0100", queried = true))

      val data = QueryNodeData(lookupReq, querying = Map(successNode, failedNode), seen = TreeMap(c1, c2), responseCount = config.concurrency, currRound = round)

      ref.setState(GatherNode, data)
      ref ! Start

      val expectedQuerying = Map(failedNode, c1, c2)

      inside(ref.stateData) { case QueryNodeData(_, querying, _, _, _, _) => querying shouldBe expectedQuerying }
    }
  }

  test("query all unqueried k-closest node if no new closer node has been discovered") {
    new Fixture {
      val defaultData = queryNodeDataDefault(0)

      val toQuery = defaultData.seen.takeRight(config.kBucketSize - 1)

      val data = defaultData.copy(seen = defaultData.seen + transform(defaultData.seen.head, _.copy(queried = true)))

      ref.setState(GatherNode, data)
      ref ! Start

      ref.stateName should equal(QueryNode)

      inside(ref.stateData) {
        case QueryNodeData(_, querying, _, _, _, lastRound) =>
          lastRound shouldBe true
          querying shouldBe toQuery
      }
    }
  }

  test("send the k-closest back to sender as list") {
    new Fixture {
      val data = queryNodeDataDefault()

      val requestor = TestProbe()

      ref.setState(Finalize, data.copy(req = data.req.copy(sender = requestor.ref)))
      ref ! Start

      requestor.expectMsg(data.seen.take(config.kBucketSize).toList.map(_._2))
    }
  }
}