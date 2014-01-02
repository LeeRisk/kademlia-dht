package com.tommo.kademlia.identity

import com.tommo.kademlia.invariant

import Id._

case class Id(bits: Seq[Bit]) {
  def distance(to: Id): Id = {
    invariant(bits.size != to.bits.size, "Must have same address space size to compute distance")
    Id(bits.zip(to.bits).map(_ match { case (x, y) => x ^ y }))
  }

  override def toString = bits.foldRight("")((x, y) => y + (if (x) "1" else "0"))
}

object Id {
  type Bit = Boolean

  def apply(bytes: Array[Byte]) = {
    new Id(for (byte <- bytes; byteToBites <- (0 to 7).map(digit => ((byte >> digit) & 1) == 1)) yield (byteToBites))
  }

  def apply(bitStr: String) = {
    invariant(!bitStr.matches("[01]+"), "String can only contain 0s or 1s")
    new Id(bitStr.foldLeft(List[Bit]())((x, y) => (y == '1') :: x))
  }

  private[identity] def apply(idSize: Int)(decimalVal: BigInt): Id = {
    invariant(decimalVal < 0, "Value can not be less than 0")
    invariant(idSize < decimalVal.bitLength, s"idSize must be greater than the unsigned binary representation of $decimalVal")
    invariant(idSize % 8 != 0, "idSize must be a multiple of a byte")

    val byteArr = decimalVal.toByteArray.reverse.dropWhile(_ == 0)
    val byteArrSize = byteArr.size - 1

    apply(Array.tabulate[Byte](idSize / 8)(index =>
      if (index > byteArrSize)
        0
      else byteArr(index)))
  }
}
