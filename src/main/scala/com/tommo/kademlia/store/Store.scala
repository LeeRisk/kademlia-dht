package com.tommo.kademlia.store

import com.tommo.kademlia.identity.Id

trait Store[V] {
	def insert(id: Id, v: V)
	def get(id: Id): Option[V]
	def remove(id: Id)
	def findCloserThan(source: Id, target: Id): List[(Id, V)] 
}
