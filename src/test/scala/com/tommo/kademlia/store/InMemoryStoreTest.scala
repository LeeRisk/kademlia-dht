package com.tommo.kademlia.store

import com.tommo.kademlia.BaseUnitTest
import com.tommo.kademlia.BaseFixture
import com.tommo.kademlia.identity.Id

class InMemoryStoreTest extends BaseUnitTest with BaseFixture {

  trait Fixture {
    val store = new InMemoryStore[Int]
    val id = aRandomId
  }

  test("insert and get") {
    new Fixture {
      store.insert(id, 1)

      store.get(id) shouldBe Some(1)
    }
  }

  test("insert same id should overwrite previous") {
    new Fixture {
      store.insert(id, 1)
      store.insert(id, 2)

      store.get(id) shouldBe Some(2)
    }
  }

  test("remove value") {
    new Fixture {
      store.insert(id, 1)
      store.remove(id)

      store.get(id) shouldBe None
    }
  }

  test("return values that are closer to target than the source") {
    new Fixture {
      val sourceId = Id("0101")
      val targetId = Id("1001")
      
      store.insert(Id("0001"), 1)
      store.insert(Id("0110"), 6)
      store.insert(Id("0111"), 7)

      store.insert(Id("1000"), 8) // expected
      store.insert(Id("1010"), 10)
      store.insert(Id("1100"), 12)
      
      store.findCloserThan(sourceId, targetId) should contain theSameElementsAs List((Id("1000"), 8), (Id("1010"), 10), (Id("1100"), 12))
    }
  }

}