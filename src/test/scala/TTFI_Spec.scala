import TTFI._

import org.specs2._
class TTFI_Spec extends Specification { def is = s2"""
	ExpSym	$es
	MulSym	$ms
	TreeSem $ts
	PushNeg	$pn
"""

	def es = {
		println(Final.ExpSym.Use.result)
  	println(Final.ExpSym.Use.result2)
    ok
	}

	def ms = {
  	println(Final.MulSym.Use.result)
    println(Final.MulSym.Use.result2)
    ok
	}

  def ts = {
    Final.TreeSem.Use.result.isRight &&
    Final.TreeSem.Use.result2.isRight &&
    Final.TreeSem.Use.result3.isLeft &&
    Final.TreeSem.Use.result4.isRight //&&
    //Final.TreeSem.Use.result2 ==== Final.TreeSem.Use.result2b &&
    //Final.TreeSem.Use.result4 ==== Final.TreeSem.Use.result4b
  }

  def pn = {
    println(Final.PushNeg.Use.result)
    println(Final.PushNeg.Use.result2)
    Initial.FP.result ==== Final.PushNeg.Use.result
  }
}