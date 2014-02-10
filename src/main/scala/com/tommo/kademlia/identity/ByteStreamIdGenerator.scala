package com.tommo.kademlia.identity

trait ByteStreamIdGenerator {
	def generateId(input: Array[Byte]): Id
}