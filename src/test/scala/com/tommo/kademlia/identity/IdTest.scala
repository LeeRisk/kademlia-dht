package com.tommo.kademlia.identity

import com.tommo.kademlia.BaseUnitTest
import org.scalatest._

class IdTest extends BaseUnitTest {
  def idFactory = Id(8)_

  test("be able to compute distance between a node with the same address size") {
    val id = idFactory(8)
    val anotherId = idFactory(3)

    val expectedDistance = idFactory(11)

    assert(id.distance(anotherId) == expectedDistance)
  }

  test("construct a id from binary string") {
    val id = Id("10")
    id.decimalVal should equal(2)
  }

  test("return the longest common prefix size") {
    val id = Id("10010011")
    val anotherId = Id("10011011")

    assert(id.longestPrefixLength(anotherId) == 4)

    val decId = idFactory(4)
    val anotherDecId = idFactory(8)

    assert(decId.longestPrefixLength(anotherDecId) == 4)
  }

  test("fail if address space size do not match for distance calculation") {
    val id = Id("10011")
    val anotherId = Id("111111")

    intercept[RuntimeException] {
      id distance anotherId
    }
  }

  test("fail if constructed bit string contains invalid characters") {
    intercept[RuntimeException] {
      Id("01InvalidSeq10")
    }
  }

  test("find all mistmatched indices") {
    val id = Id("00100")
    val anotherId = Id("11111")

    id.findAllNonMatchingFromRight(anotherId) should contain inOrderOnly (4, 3, 1, 0)

    id.findAllNonMatchingFromRight(id) shouldBe empty
  }

  test("toString should represent a binary number") {
    val id = idFactory(4)
    
    id.toString shouldBe "00000100"
  }

  test("return the closer node when compared against two in Ordering") {
    val id = Id("1010")
    val order = new id.Order

    var cIdOne = Id("0100")
    var cIdTwo = Id("0010")

    order.compare(cIdOne, cIdTwo) should be < 0

    cIdOne = Id("1110")
    cIdTwo = Id("1111")

    order.compare(cIdOne, cIdTwo) should be > 0

    cIdOne = Id("1111")
    cIdTwo = Id("1111")

    order.compare(cIdOne, cIdTwo) shouldBe 0

  }
}
