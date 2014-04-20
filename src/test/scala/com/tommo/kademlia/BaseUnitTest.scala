package com.tommo.kademlia

import org.scalatest.FunSuiteLike
import org.scalatest.Inside
import org.scalatest.Inspectors
import org.scalatest.Matchers
import org.scalatest.OptionValues
import org.scalatest.mock.MockitoSugar

trait BaseUnitTest extends Matchers with FunSuiteLike with OptionValues with Inside with MockitoSugar 