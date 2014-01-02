package com.tommo.kademlia.identity

trait IdGenerator {
	def generateId(input: Array[Byte]): Id
}