package com.tommo.kademlia

import LookupActor._

import akka.testkit.{TestFSMRef, TestProbe}
import akka.actor.{ ActorRef, Actor }
import akka.actor.FSM._

import scala.collection.immutable.TreeMap
import scala.concurrent.duration._

import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.identity.Id

class LookupActorTest extends BaseTestKit("LookupSpec") {
  trait Fixture {
    val kBucketActor = testActor

    val config = mockConfig
    
    val ref = TestFSMRef(new LookupActor(kBucketActor)(config))
    val underlyingFsm = ref.underlyingActor
    val toFindId = mockZeroId(4)
    val kRequest = KClosestRequest(toFindId, testActor)
    implicit val order = (new kRequest.id.Order).reverse

    def transform(pair: (Id, NodeQuery), tFn: NodeQuery => NodeQuery) = (pair._1, tFn(pair._2))

    val nodeQueryDefaults = QueryNodeData(req = kRequest, seen = TreeMap())
    val round = nodeQueryDefaults.currRound
    val nextRound = round + 1

    def queryNodeDataDefault(toQuery: Int = config.concurrency) = QueryNodeData(kRequest, mockNodeMap.takeRight(toQuery), mockNodeMap)

    lazy val mockNodeMap = TreeMap(mockActorNodePair("1000"), mockActorNodePair("1001"), mockActorNodePair("1011"), mockActorNodePair("1101"))

    def mockActorNodePair(id: String, round: Int = round, queried: Boolean = false) = actorNodeToKeyPair(mockActorNode(id), queried, round)
    def mockActorNode(id: String) = ActorNode(testActor, Id(id))
  }

  "A LookupActor" should "start in Initial state with Empty data" in new Fixture {
    ref.stateName should equal(LookupActor.Initial)
    ref.stateData should equal(LookupActor.Empty)
  }

  it should "send to the KBucket Actor the id to get the k-closest locally known nodes" in new Fixture {
    ref ! toFindId
    expectMsg(GetKClosest(toFindId, mockConfig.kBucketSize))

    ref.stateName should equal(QueryKBucket)
    ref.stateData should equal(QueryKBucketData(kRequest))

  }

  it should "go to QueryNode state when the k-closest locally known nodes is received from the KBucket Actor" in new Fixture {
    ref.setState(QueryKBucket, QueryKBucketData(kRequest))
    
    ref ! KClosest(mockActorNode("1010") :: Nil)

    ref.stateName should equal(QueryNode)
  }

  it should "query α concurrently for each round" in new Fixture {
    ref.setState(QueryKBucket, QueryKBucketData(kRequest))
    ref.setState(QueryNode, queryNodeDataDefault())

    expectMsgAllOf(GetKClosest(toFindId, config.kBucketSize), GetKClosest(toFindId, config.kBucketSize))
  }

  it should "add newly discovered nodes that is returned from a queried node and update the node's status to queried" in new Fixture {
    val data = queryNodeDataDefault()

    ref.setState(QueryNode, data)

    val (queryId, queryState) = data.querying.head

    val anId = "0001"
    ref ! KClosestRemote(ActorNode(queryState.ref, queryId), List(mockActorNode(anId)))

    inside(ref.stateData) {
      case QueryNodeData(_, querying, seen, _, _, _) =>
        querying shouldBe data.querying + (queryId -> queryState.copy(queried = true))
        seen shouldBe data.seen + mockActorNodePair(anId, round = 2)
    }
  }

  it should "go from QueryNode to GatherNode after the specified config timeout" in new Fixture {
    val data = queryNodeDataDefault()

    underlyingFsm onTransition {
      case QueryNode -> GatherNode =>
        inside(underlyingFsm.nextStateData) {
          case QueryNodeData(_, _, _, responseCount, currRound, _) =>
            if (currRound == round) responseCount shouldBe 2
        }
    }

    ref.setState(QueryKBucket, QueryKBucketData(kRequest))
    ref.setState(QueryNode, data)

    awaitAssert(ref.stateName should equal(GatherNode), max = 600 millis, interval = 100 micro)
  }

  it should "update all successfully queried nodes that are in the seen map" in new Fixture {
    val defaultData = queryNodeDataDefault()
    val closestQueryingUpdated = transform(defaultData.querying.head, _.copy(queried = true))
    val data = defaultData.copy(querying = defaultData.querying + closestQueryingUpdated)

    ref.setState(GatherNode, data)
    ref ! Start

    inside(ref.stateData) { case QueryNodeData(_, _, seen, _, _, _) => seen shouldBe data.seen + closestQueryingUpdated }
  }

  it should "continue querying up to α unqueried nodes if there is a newly discovered node that is closer than the previous" in new Fixture {
    val (c1Queried, c2EvenCloser) = (mockActorNodePair("0010", queried = true), mockActorNodePair("0001", round = nextRound))

    val data = QueryNodeData(kRequest, seen = TreeMap(c1Queried, c2EvenCloser), responseCount = 0, currRound = round)

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

  it should "query nodes that failed to respond in previous round in addition to the newly discovered ones" in new Fixture {
    val (c1, c2, failedNode, successNode) = (mockActorNodePair("0001", round = nextRound), mockActorNodePair("0010", round = nextRound),
      mockActorNodePair("1000"), mockActorNodePair("0100", queried = true))

    val data = QueryNodeData(kRequest, querying = Map(successNode, failedNode), seen = TreeMap(c1, c2), responseCount = config.concurrency, currRound = round)

    ref.setState(GatherNode, data)
    ref ! Start

    val expectedQuerying = Map(failedNode, c1, c2)

    inside(ref.stateData) { case QueryNodeData(_, querying, _, _, _, _) => querying shouldBe expectedQuerying }
  }

  it should "query all unqueried k-closest node if no new closer node has been discovered" in new Fixture {
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

  it should "send the k-closest back to sender as list" in new Fixture {
    val data = queryNodeDataDefault()

    val requestor = TestProbe()
    
    ref.setState(Finalize, data.copy(req = data.req.copy(sender = requestor.ref)))
    ref ! Start

    requestor.expectMsg(data.seen.take(config.kBucketSize).toList.map(_._2))
  }

}