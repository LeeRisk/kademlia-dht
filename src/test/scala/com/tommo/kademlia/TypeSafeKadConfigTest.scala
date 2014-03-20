package com.tommo.kademlia

import com.tommo.kademlia.protocol.Host
import com.typesafe.config._

import scala.concurrent.duration._
import java.util.Properties

class KadConfigTest extends BaseUnitTest {
  "KadConfig" should "load config file from tommo-kad namespace" in {
	  val conf = TypeSafeKadConfig(); // loads config from /src/main/resources/application.conf
	  assert(conf.host == Host("127.0.0.1:2552"))
	  assert(conf.kBucketSize == 10)
	  assert(conf.addressSpace == 160)
	  
	  assert(conf.concurrency == 2)
	  
	  assert(conf.responseTimeout == (3 second))
	  assert(conf.roundTimeOut == (1 second))
	  
  }
  
  it should "fail custom config does not contain the defined properties" in {
    intercept[RuntimeException] {
    	new TypeSafeKadConfig(ConfigFactory.parseProperties(new Properties())) // empty properties
    }
  }
}

