package com.tommo.kademlia.protocol

import com.tommo.kademlia.BaseUnitTest

class HostTest extends BaseUnitTest {
  test("can be constructed from string") {
    val host = Host("hostName:8080")

    import host._

    assert(hostname == "hostName")
    assert(port == 8080)
  }

  test("fail if an invalid string is provided") {
    intercept[RuntimeException] {
      Host("someinvalidformat:9090:20")
    }
  }
}