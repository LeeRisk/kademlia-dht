package com.tommo.kademlia.protocol

import com.tommo.kademlia.BaseUnitTest

class HostTest extends BaseUnitTest {
  "A host" can "be constructed from a string" in {
    val host = Host("hostName:8080")

    import host._

    assert(hostname == "hostName")
    assert(port == 8080)
  }

  it should "fail if an invalid string is provided" in {
    intercept[RuntimeException] {
      Host("someinvalidformat:9090:20")
    }
  }
}