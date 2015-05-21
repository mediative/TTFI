package finals

import initial.FP.{ Exp => Initial, Lit, Neg, Add }

object Isomorphic {
  import ExpSym.ExpSym

  def apply[repr[_]](e: Initial[Integer])(f: Initial[Integer] => Initial[Integer])(implicit s: ExpSym[repr]): repr[Integer] =
    f(e)

  def apply[repr[_]](e: ExpSym[Initial] => Initial[Integer])(f: Initial[Integer] => Initial[Integer])(implicit s: ExpSym[repr]): repr[Integer] =
    Exp_Final(f(e(ExpSym_Initial)))(s)

  implicit object ExpSym_Initial extends ExpSym[Initial] {
    def lit = (x: Integer) => Lit(x)
    def neg = x => Neg(x)
    def add = x => y => Add(x, y)
  }

  implicit def Exp_Final[repr[_]](x: Initial[Integer])(implicit s: ExpSym[repr]): repr[Integer] =
    x match {
      case Lit(e) => s.lit(e)
      case Neg(e) => s.neg(Exp_Final(e)(s))
      case Add(e1, e2) => s.add(Exp_Final(e1)(s))(Exp_Final(e2)(s))
    }
}
