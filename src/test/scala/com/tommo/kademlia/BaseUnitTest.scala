package com.tommo.kademlia

import org.scalatest._

import com.tommo.kademlia.protocol.Host
import com.tommo.kademlia.identity._

trait BaseUnitTest extends FlatSpecLike with Matchers with OptionValues with Inside with Inspectors 