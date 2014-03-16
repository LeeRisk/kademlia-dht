package com.tommo.kademlia.identity

import com.tommo.kademlia.BaseUnitTest
import org.scalatest._

class IdTest extends BaseUnitTest {
  def idFactory = Id(8)_

  "Id" should "be able to compute distance between a node with the same address size" in {
    val id = idFactory(8)
    val anotherId = idFactory(3)

    val expectedDistance = idFactory(11)

    assert(id.distance(anotherId) == expectedDistance)
  }

  it should "construct a string of bits" in {
    val id = Id("10")
    id.decimalVal should equal(2)
  }

  it should "return the longest common prefix size" in {
    val id = Id("10010011")
    val anotherId = Id("10011011")

    assert(id.longestPrefixLength(anotherId) == 4)

    val decId = idFactory(4)
    val anotherDecId = idFactory(8)

    println(decId)
    println(anotherDecId)

    assert(decId.longestPrefixLength(anotherDecId) == 4)
  }

  it should "fail if address space size do not match for distance calculation" in {
    val id = Id("10011")
    val anotherId = Id("111111")

    intercept[RuntimeException] {
      id distance anotherId
    }
  }

  it should "fail if constructed bit string contains invalid characters" in {
    intercept[RuntimeException] {
      Id("01InvalidSeq10")
    }
  }

  it should "find all mistmatched indices" in {
    val id = Id("00100")
    val anotherId = Id("11111")

    id.findAllNonMatchingFromRight(anotherId) should contain inOrderOnly (4, 3, 1, 0)

    id.findAllNonMatchingFromRight(id) shouldBe empty
  }

  it should "correct toString" in {
    val id = idFactory(4)

    id.toString shouldBe "00000100"

  }

  it should "return the closer node when compared against two in Ordering" in {
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
