import TTFI._

import org.specs2._
class Fix_Spec extends Specification { def is = s2"""
	Fix should not blow the stack $stackSafe
"""

	def stackSafe = {

		// http://en.wikipedia.org/wiki/Fixed-point_combinator#Lazy_functional_implementation
 		def factabs(fact: Int => Int)(x: Int) = 
 			if(x == 0) 1
 			else x * fact(x-1)

 		Final.TreeSem.OpenRecursion.Fix(factabs)(10) ==== 3628800
	}

}