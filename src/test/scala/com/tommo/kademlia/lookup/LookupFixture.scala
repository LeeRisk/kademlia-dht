package com.tommo.kademlia.lookup

import akka.testkit.TestProbe
import scala.collection.immutable.TreeMap

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.{ BaseFixture, BaseTestKit }
import LookupNode._

abstract class LookupFixture(testKit: BaseTestKit) extends BaseFixture {
  import mockConfig._

  import testKit._

  val kClosestProbe = TestProbe()
  val reqSendProbe = TestProbe()

  val toFindId = mockZeroId(4)
  val lookupReq = Lookup(toFindId, testActor)

  implicit val order = new lookupReq.id.SelfOrder

  def transform(pair: (Id, NodeQuery), tFn: NodeQuery => NodeQuery) = (pair._1, tFn(pair._2))

  val nodeQueryDefaults = QueryNodeData(req = lookupReq, seen = TreeMap())
  val round = nodeQueryDefaults.currRound
  val nextRound = round + 1

  def queryNodeDataDefault(toQuery: Int = roundConcurrency) = takeAndUpdate(nodeQueryDefaults.copy(seen = mockSeen), toQuery)

  lazy val mockSeen = TreeMap(mockActorNodePair("1000"), mockActorNodePair("1001"), mockActorNodePair("1011"), mockActorNodePair("1101"))

  def mockActorNodePair(id: String, round: Int = round, respond: Boolean = false) = actorNodeToKeyPair(mockActorNode(id), round, respond)
}