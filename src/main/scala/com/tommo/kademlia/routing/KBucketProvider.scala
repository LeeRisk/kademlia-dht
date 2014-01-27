package com.tommo.kademlia.routing

trait KBucketProvider {
	def newKBucketEntry = KBucket(16)
}