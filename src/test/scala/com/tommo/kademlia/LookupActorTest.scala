package com.tommo.kademlia

import akka.testkit.TestFSMRef
import akka.actor.{ ActorRef, Actor }

import LookupActor._
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.identity.Id
import scala.collection.immutable.TreeMap
import scala.concurrent.duration._

class LookupActorTest extends BaseTestKit("LookupSpec") {

  trait ActorFsmFixture {
    val kBucketActor = testActor
    val config = mockConfig
    val ref = TestFSMRef(new LookupActor(kBucketActor)(config))
    val underlying = ref.underlyingActor
  }

  val id = mockZeroId(4)
  implicit val order = new id.Order

  def actorNode(id: String) = ActorNode(testActor, Id(id))
  def actorNodePair(id: String, queried: Boolean = false, round: Int = 1) = actorNodeToKeyPair(actorNode(id), queried, round);

  val mockLocalNodes = List(actorNode("1000"), actorNode("1001"), actorNode("1011"), actorNode("1101")) // must be ordered with mockZeroId

  def toQueryNode(mockNodes: List[ActorNode])(qFn: Id => Boolean) =  mockNodes.map(x => actorNodeToKeyPair(x, queried = qFn(x.id)))
  
  def mockQueryNodeData(mockNodes: List[ActorNode], numQuery: Int = mockConfig.concurrency)(qFn: Id => Boolean) = {
    val seen = TreeMap(mockLocalNodes.map(x => actorNodeToKeyPair(x)): _*)
    val query = seen.takeRight(numQuery).map(Function.tupled((id, nodeQuery) => (id, nodeQuery.copy(queried = qFn(id)))))
    QueryNodeData(id, query, seen)
  }

  val initQueryState = mockQueryNodeData(mockLocalNodes)(x => false)

  "A LookupActor" should "start in Initial state with Empty data" in new ActorFsmFixture {
    ref.stateName should equal(LookupActor.Initial)
    ref.stateData should equal(LookupActor.Empty)
  }

  it should "send to the kbucketActor the id to get the k-closest locally known nodes" in new ActorFsmFixture {
    ref ! id
    expectMsg(GetKClosest(id, mockConfig.kBucketSize))
  }

  it should "go to QueryKBucketSet state when find k-closest request from id is received" in new ActorFsmFixture {
    ref ! id
    ref.stateName should equal(QueryKBucket)
    ref.stateData should equal(QueryKBucketData(id))
  }

  it should "go to QueryNode state when the k-closest node is received from the KBucket" in new ActorFsmFixture {
    ref.setState(QueryKBucket, QueryKBucketData(id))

    ref ! KClosest(mockLocalNodes)

    ref.stateName should equal(QueryNode)
  }

  it should "query α concurrently for each round" in new ActorFsmFixture {
    ref.setState(QueryKBucket, QueryKBucketData(id))
    ref.setState(QueryNode, initQueryState)

    expectMsgAllOf(GetKClosest(id, mockConfig.kBucketSize), GetKClosest(id, mockConfig.kBucketSize))
  }

  it should "update the QueryState of the node that returned kClosest that was queried in the current round and add the result to seen" in new ActorFsmFixture {
    val queryNodeState = initQueryState

    ref.setState(QueryNode, queryNodeState)

    val (mockId, queryState) = queryNodeState.querying.head

    val mockKResponse = KClosestRemote(ActorNode(queryState.ref, mockId), List(actorNode("0001")))
    ref ! mockKResponse

    val expectedQuerying = queryNodeState.querying + (mockId -> queryState.copy(queried = true))
    val expectedSeen = queryNodeState.seen + actorNodeToKeyPair(actorNode("0001"), round = 2)

    inside(ref.stateData) {
      case QueryNodeData(_, querying, seen, _, _) =>
        querying shouldBe expectedQuerying
        seen shouldBe expectedSeen
    }
  }

  it should "go to QueryNode -> GatherNode after the specified config timeout" in new ActorFsmFixture {
    import akka.actor.FSM._

    val queryNodeState = initQueryState

    underlying onTransition {
      case QueryNode -> GatherNode =>
        inside(ref.stateData) {
          case QueryNodeData(_, _, _, _, responseCount) =>
            responseCount shouldBe queryNodeState.querying.size - 1
        }
    }

    ref.setState(QueryKBucket, QueryKBucketData(id))
    ref.setState(QueryNode, queryNodeState)

    awaitAssert(ref.stateName should equal(GatherNode), max = 600 millis, interval = 100 micro) // allow some time

  }

  it should "update all successfully queried nodes in seen" in new ActorFsmFixture {
    val first = mockLocalNodes.take(1)
    val mockStateData = mockQueryNodeData(mockLocalNodes)(x => first.exists(_.id == x))

    ref.setState(GatherNode, mockStateData)
    ref ! InitiateGather

    val expectedSeen = mockStateData.seen ++ mockStateData.querying

    inside(ref.stateData) { case QueryNodeData(_, _, seen, _, _) => seen shouldBe expectedSeen }
  }

  it should "continue querying up to α unqueried nodes if there is a newly discovered node that is closer than the previous" in new ActorFsmFixture {
    val (c1Queried, c2PrevRound, c3) = (actorNodePair("0001", true), actorNodePair("0010"), actorNodePair("0011", round = 3))

    val data = QueryNodeData(id, seen = TreeMap(c1Queried, c2PrevRound, c3), responseCount = 300, currRound = 2)

    ref.setState(GatherNode, data)
    ref ! InitiateGather

    ref.stateName shouldBe QueryNode

    inside(ref.stateData) {
      case QueryNodeData(_, querying, _, responseCount, nextRound) =>
        querying shouldBe Map(c3, c2PrevRound)
        responseCount shouldBe 0
        nextRound shouldBe 3
    }
  }

  it should "query nodes that failed to respond in previous round in addition to the newly discovered ones" in new ActorFsmFixture {
    val (round, nextRound) = (1, 2)
    val (c1, c2, failedNode, successNode) = (actorNodePair("0001", round = nextRound), actorNodePair("0010", round = nextRound), actorNodePair("1000", round = round), actorNodePair("0100", queried = true, round = round))

    val data = QueryNodeData(id, querying = Map(successNode, failedNode), seen = TreeMap(c1, c2), responseCount = 300, currRound = round)

    ref.setState(GatherNode, data)
    ref ! InitiateGather

    val expectedQuerying = Map(failedNode, c1, c2)

    inside(ref.stateData) {
      case QueryNodeData(_, querying, _, _, _) =>
        querying shouldBe expectedQuerying
    }
  }
  
  it should "query all unqueried k-closest node if no new closer node has been discovered" in new ActorFsmFixture { 
       val (round, nextRound) = (1, 2)
       
//       val (closestUnqueried, closestQueried) =
         val k = mockQueryNodeData(mockLocalNodes)(_ == Id("1000"))
  }
  
  
}