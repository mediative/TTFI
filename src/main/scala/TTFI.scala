object TTFI {

  object Final {
    // {{{ ExpSym

    trait Repr[T]

    object ExpSym {
      trait ExpSym[repr[_]] {
        def lit: Integer => repr[Integer]
        def neg: repr[Integer] => repr[Integer]
        def add: repr[Integer] => repr[Integer] => repr[Integer]
      }
      // cleaner 'constructors'
      def Lit[repr[_]](x: Integer)(implicit s1: ExpSym[repr]): repr[Integer] = {
        s1.lit(x)
      }
      def Neg[repr[_]](e: repr[Integer])(implicit s1: ExpSym[repr]): repr[Integer] = {
        s1.neg(e)
      }
      def Add[repr[_]](e1: repr[Integer])(e2: repr[Integer])(implicit s1: ExpSym[repr]): repr[Integer] = {
        s1.add(e1)(e2)
      }

      // container to hold the result of 'Eval'-ing an expression
      case class Eval[T](value: T) extends Repr[T]
      // 'Eval' interpreter definition
      implicit object ExpSym_Eval extends ExpSym[Eval] {
        def lit = Eval(_)
        def neg = x => Eval(-x.value)
        def add = x => y => Eval(x.value + y.value)
      }
      // run the 'Eval' interpreter
      def eval[T]: Eval[T] => T = _.value

      // container to hold the result of 'pretty-printing' an expression
      case class Debug[T](debug: String) extends Repr[T]
      // definition of pretty-printing interpreter
      implicit object ExpSym_Debug extends ExpSym[Debug] {
        def lit = x => Debug(x.toString)
        def neg = x => Debug(s"(-${x.debug})")
        def add = x => y => Debug(s"(${x.debug} + ${y.debug})")
      }
      // run the pretty-printing interpreter
      def view[T]: Debug[T] => String = _.debug

      object Use {
        def tf1[repr[_]](implicit s1: ExpSym[repr]): repr[Integer] = {
          // s1.add(s1.lit(8))(s1.neg(s1.add(s1.lit(1))(s1.lit(2))))
          Add(Lit[repr](8))(Neg(Add(Lit[repr](1))(Lit[repr](2))))
        }
        def tf2[repr[_]](implicit s1: ExpSym[repr]): repr[Integer] = {
          // s1.neg(tf1[repr])
          Neg(tf1[repr])
        }

        // TODO: obviate need to pass type explicitly 
        // in haskell, the type of 'eval' tells the compiler which type dictionary
        // to look at. it can do that because overlapping instances aren't allowed
        // by default. in scala, however, you can have multiple overlapping
        // implicits. the dispatch mechanism then is based on
        // <http://stackoverflow.com/questions/5598085/where-does-scala-look-for-implicits>
        // what we need is a way to thread a type to underlying argument

        val result = eval(tf1[Eval])
        val result2 = view(tf1[Debug])
      }
    }

    // }}}

    // {{{ MulSym

    object MulSym {
      import ExpSym._
      // add multiplication operation to the Exp dsl
      trait MulSym[repr[_]] {
        def mul: repr[Integer] => repr[Integer] => repr[Integer]
      }
      // cleaner 'constructor'
      def Mul[repr[_]](e1: repr[Integer])(e2: repr[Integer])(implicit s1: MulSym[repr]): repr[Integer] = {
        s1.mul(e1)(e2)
      }

      // multiplication for Integer domain
      implicit object MulSym_Eval extends MulSym[Eval] {
        def mul = x => y => Eval(x.value * y.value)
      }
      implicit object MulSym_Debug extends MulSym[Debug] {
        def mul = x => y => Debug(s"(${x.debug} * ${y.debug})")
      }

      object Use {
        def tfm1[repr[_]](implicit s1: ExpSym[repr], s2: MulSym[repr]) = {
          // s1.add(s1.lit(8))(s1.neg(s2.mul(s1.lit(1))(s1.lit(2))))
          Add(Lit[repr](8))(Neg(Mul(Lit[repr](1))(Lit[repr](2))))
        }

        val result = eval(tfm1[Eval])
        val result2 = view(tfm1[Debug])
      }
    }

    // }}}

    // {{{ PushNeg

    object PushNeg {
      // make the context on which the operation depends explicit
      sealed trait Ctx
      case object Pos extends Ctx
      case object Neg extends Ctx

      // [[http://stackoverflow.com/a/8736360][typed lambdas]]
      // this trait allows us to provide nicer type signatures. without this,
      // instead of Ctx_=>[repr]#τ we'd be using something like the following
      // ({ type λ[+T] = Ctx => repr[T] })#λ
      trait Ctx_=>[repr[_]] {
        type τ[T] = Ctx => repr[T]
      }

      // PushNeg.apply === pushNeg (in Initial version). pass in the 'no-op'/
      // base context
      def apply[repr[_]](e: Ctx => repr[Integer]): repr[Integer] = e(Pos)

      import ExpSym._
      // what we'd like is something like the following:
      // implicit object ExpSym_Ctx[repr](implicit s1: ExpSym[repr]) extends ExpSym[Ctx_=>[repr]#τ]
      //
      // due to limitation that scala objects need to have a concrete type, this
      // needs to be an 'implicit class'. _x is needed due to the requirement that
      // implicit classes have one argument
      implicit class ExpSym_Ctx[repr[_]](_x: Any = null)(implicit s1: ExpSym[repr]) extends ExpSym[Ctx_=>[repr]#τ] {
        def lit = (x: Integer) => (ctx: Ctx) => ctx match {
          case Pos => s1.lit(x)
          case Neg => s1.neg(s1.lit(x))
        }
        def neg = x => (ctx: Ctx) => ctx match {
          case Pos => x(Neg)
          case Neg => x(Pos)
        }
        def add = x => y => (ctx: Ctx) => s1.add(x(ctx))(y(ctx))
      }

      import MulSym._
      implicit class MulSym_Ctx[repr[_]](_x: Any = null)(implicit s1: MulSym[repr]) extends MulSym[Ctx_=>[repr]#τ] {
        def mul = x => y => (ctx: Ctx) => ctx match {
          case Pos => s1.mul(x(Pos))(y(Pos))
          case Neg => s1.mul(x(Pos))(y(Neg))
        }
      }

      object Use {
        import ExpSym.{ Debug, view }
        import ExpSym.Use.tf1
        val result = view(PushNeg(tf1[Ctx_=>[Debug]#τ](())))
 
        import MulSym.Use.tfm1
        val result2 = view(PushNeg(tfm1[Ctx_=>[Debug]#τ]((), ())))
      }
    }

    // }}}

    // {{{ TreeSem

    object TreeSem {
      // our extensible serialization format
      sealed trait Tree[+T]
      case class Leaf[+T](data: String) extends Tree[T]
      case class Node[+T](data: String, rest: Seq[Tree[T]]) extends Tree[T]

      import ExpSym._
      // serializer for ExpSym
      implicit object ExpSym_Tree extends ExpSym[Tree] {
        def lit = (x: Integer) => Node("Lit", Seq(Leaf(s"${x}")))
        def neg = x => Node("Neg", Seq(x))
        def add = x => y => Node("Add", Seq(x, y))
      }
      // run the serializer
      def toTree[T]: Tree[T] => Tree[T] = identity

      val tf1_tree = toTree(ExpSym.Use.tf1[Tree])

      import MulSym._
      // extending the serializer for MulSym
      implicit object MulSym_Tree extends MulSym[Tree] {
        def mul = x => y => Node("Mul", Seq(x, y))
      }
      val tfm1_tree = toTree(MulSym.Use.tfm1[Tree])

      // deserialization
      type ErrMsg = String
      def safeRead(x: String): Either[ErrMsg, Integer] = try {
        Right(x.toInt)
      } catch {
        case e: NumberFormatException => Left(s"Read error in ${x}")
      }

      // {{{ closed recursion: fromTree, fromTreeExt

      object ClosedRecursion {
        // this, given a Tree gives ExpSym[repr] => repr[Integer] w/ error msgs
        def fromTree[repr[_]](x: Tree[Integer])(implicit s1: ExpSym[repr]): Either[ErrMsg, repr[Integer]] = x match {
          case Node("Lit", Seq(Leaf(x))) => safeRead(x).right.map(s1.lit(_))
          case Node("Neg", Seq(x)) => fromTree[repr](x).right.map(s1.neg(_))
          case Node("Add", Seq(x, y)) => for {
            a <- fromTree[repr](x).right
            b <- fromTree[repr](y).right
          } yield s1.add(a)(b)
          case _ => Left(s"Parse error in ${x}")
        }

        def fromTreeExt[repr[_]](x: Tree[Integer])(implicit s1: MulSym[repr], s2: ExpSym[repr]): Either[ErrMsg, repr[Integer]] = x match {
          case Node("Mul", Seq(x, y)) => for {
            a <- fromTree[repr](x).right
            b <- fromTree[repr](y).right
          } yield s1.mul(a)(b)
          case x => fromTree[repr](x)
        }
      }

      // }}}

      // {{{ open recursion: fromTree, fromTreeExt

      object OpenRecursion {
        import scala.annotation.tailrec

        // not tail-recursive
        def fix[A, B](f: (A => B) => (A => B)): A => B = {
          f((x: A) => fix(f)(x))
        }

        // // tail-recursive version of fixpoint, by doubling composition.
        // // NOTE: this doesn't work if 'f' is curried. i.e., 'B' cannot be of the
        // // form 'C => ...'. the reason is that in that case FixException escapes
        // // the context. if 'B' is not a concrete value (but is a function) then
        // // 'FixException' is only thrown when it gets applied to something, at
        // // which point it's too late (unless we override the .apply function, by
        // // creating a 'Fix' object?)
        // object Fix {
        //   case object FixException extends RuntimeException
        //   @tailrec def apply[A, B](f: (A => B) => (A => B))(x: A): B = try {
        //      f(_ => throw FixException)(x)
        //   } catch {
        //     case FixException => Fix(f andThen f)(x)
        //   }
        // }

        // http://rosettacode.org/wiki/Y_combinator#Scala
        object Fix {
          def apply[A,B](f: (A => B) => (A => B)) = {
            case class W(wf: W => A => B) {
              def apply(w: W) = wf(w)
            }
            val g: W => A => B = w => f(w(w))(_)
            g(W(g))
          }  
        }
        


        // {{{ FixCurried

        object FixCurried {
          def foo[repr[_]](self: Tree[Integer] => (ExpSym[repr] => Either[ErrMsg, repr[Integer]]))(x: Tree[Integer])(s1: ExpSym[repr]) = x match {
            case Node("Lit", Seq(Leaf(x))) => safeRead(x).right.map(s1.lit(_))
            case Node("Neg", Seq(x)) => self(x)(s1).right.map(s1.neg(_))
            case Node("Add", Seq(x, y)) => for {
              a <- self(x)(s1).right
              b <- self(y)(s1).right
            } yield s1.add(a)(b)
            case _ => Left(s"Parse error in ${x}")
          }
          def bar[repr[_]](x: Tree[Integer])(implicit s1: ExpSym[repr]): Either[ErrMsg, repr[Integer]] =
            Fix(foo[repr])(x)(s1)
          // if you uncomment the line below, FixException gets thrown, which is
          // now what we intended. the FixException escapes our exception
          // catching mechanism
          // val hmm = OpenRecursion.FixCurried.bar[Debug](tf1_tree)
        }

        // }}}

        def fromTree_[repr[_]](s1: ExpSym[repr])(self: Tree[Integer] => Either[ErrMsg, repr[Integer]])(x: Tree[Integer]) = x match {
          case Node("Lit", Seq(Leaf(x))) => safeRead(x).right.map(s1.lit(_))
          case Node("Neg", Seq(x)) => self(x).right.map(s1.neg(_))
          case Node("Add", Seq(x, y)) => for {
            a <- self(x).right
            b <- self(y).right
          } yield s1.add(a)(b)
          case _ => Left(s"Parse error in ${x}")
        }
        def fromTree[repr[_]](x: Tree[Integer])(implicit s1: ExpSym[repr]) = Fix(fromTree_[repr](s1))(x)

        def fromTreeExt_[repr[_]](s1: (MulSym[repr], ExpSym[repr]))(self: Tree[Integer] => Either[ErrMsg, repr[Integer]])(x: Tree[Integer]): Either[ErrMsg, repr[Integer]] = x match {
          case Node("Mul", Seq(x, y)) => for {
            a <- self(x).right
            b <- self(y).right
          } yield s1._1.mul(a)(b)
          case x => fromTree_(s1._2)(self)(x)
        }
        def fromTreeExt[repr[_]](x: Tree[Integer])(implicit s1: MulSym[repr], s2: ExpSym[repr]): Either[ErrMsg, repr[Integer]] = Fix(fromTreeExt_[repr]((s1, s2)))(x)

        // {{{ Poly

        // this solves the deserialization problem by deserializing to functions
        object Poly2 {
          sealed trait Tree[+T]
          case class Tree_[T, +U](x: TreeV[T], rest: Seq[T => Tree[U]]) extends Tree[U]
          // type TreeCont[A, B] = A => Tree[B]
          // type TreeCExp[A, B] = Seq[TreeCont[A, B]]

          sealed trait TreeV[+T]
          case class Leaf[+T](data: String) extends TreeV[T]
          case class Node[+T](data: String, rest: Seq[Tree[T]]) extends TreeV[T]

          def fromView[T]: TreeV[T] => Tree[T] = Tree_(_, Seq())
          def toView[T]: Tree[T] => TreeV[T] = ???

          // if: children can be translated using ExpSym[repr]
          // then: this expr can be translated
          def fromTree_[repr[_]](self: Tree[Integer] => Either[ErrMsg, (ExpSym[repr] => repr[Integer])])(x: Tree[Integer]) = ???
          def fromTree[repr[_]](x: Tree[Integer]): Either[ErrMsg, (ExpSym[repr] => repr[Integer])] =
            Fix(fromTree_[repr])(x)

          // if: children can be translated using ExpSym[repr], MulSym[repr]
          // then: this expr can be translated
          // =====
          // expr could either be translated with MulSym[repr]
          // or, expr could be translated with ExpSym[repr]
          // :self[ExpSym](repr): == :self[MulSym](repr):
          // =====
          // HMM: or(?), we proceed bottom up, and at every stage the transform
          // yields optional functions taking interpreters. the parsing is a
          // partial process reflected by the fact the functions are enclosed in
          // Options. now say, we have 'n' such deserializer definitions, then
          // we can combine them together into a single (X)OR based
          // deserializer, which runs each them all in parallel. the set of all
          // deserializer semantics needed would depend on the thing being
          // deserialized, but once deserialized you'd have a partial result,
          // which could be used to yield values in different abstract domains
          // without re-parsing.
          // now, what we want to pass to the fixpoint is M[A[r] => x[r] => r[T]]
          // as self. so in some manner we need to either show:
          // - :M[A[r] => x[r] => r[T]]: == :M[A[r] => r[T]]:
          //   now, the zseq paper demonstrates the right abstraction for
          //   this. you want to parameterize on the start and end of the arrow
          //   sequence in this case, i.e. both will be represented as
          //   S[A[r], r[T]]
          // - proceed bottom up in some sense, so that we don't have to specify
          //   in the fixpoint what the children need.
          def combine[A[_[_]], x[_[_]], r[_], T](self: Tree[T] => Either[ErrMsg, A[r] => r[T]], els: Tree[T] => Either[ErrMsg, x[r] => r[T]]): Tree[T] => Either[ErrMsg, A[r] => x[r] => r[T]] = (x: Tree[T]) => {
            val fOpt = self(x)
            val gOpt = els(x)
            (fOpt, gOpt) match {
              case (Left(x), Left(y)) => Left(s"${x}\n${y}")
              case (Right(f), Left(_)) => Right(a => x => f(a))
              case (Left(_), Right(g)) => Right(a => x => g(x))
              case _ => throw new Exception("Impossible happened!") // unify?
            }
          }
          def fromTreeExt_[repr[_]](self: Tree[Integer] => Either[ErrMsg, (ExpSym[repr] => MulSym[repr] => repr[Integer])])(x: Tree[Integer]): Either[ErrMsg, (ExpSym[repr] => MulSym[repr] => repr[Integer])] = ???
          def fromTreeExt[repr[_]](x: Tree[Integer]): Either[ErrMsg, (ExpSym[repr] => MulSym[repr] => repr[Integer])] =
            Fix(fromTreeExt_[repr])(x)
        }

        // object Poly {
        //   def fromTree_[repr[_]](self: ((Tree[Integer], ExpSym[repr])) => Either[ErrMsg, repr[Integer]])(args: (Tree[Integer], ExpSym[repr])) = args match {
        //     case (x, s1) => x match {
        //       case Node("Lit", Seq(Leaf(x))) => safeRead(x).right.map(s1.lit(_))
        //       case Node("Neg", Seq(x)) => self((x, s1)).right.map(s1.neg(_))
        //       case Node("Add", Seq(x, y)) => for {
        //         a <- self((x, s1)).right
        //         b <- self((y, s1)).right
        //       } yield s1.add(a)(b)
        //       case _ => Left(s"Parse error in ${x}")
        //     }
        //   }
        //   def fromTree[repr[_]](x: Tree[Integer])(implicit s1: ExpSym[repr]): Either[ErrMsg, repr[Integer]] =
        //     Fix(fromTree_[repr])((x, s1))

        //   def fromTreeExt_[repr[_]](self: ((Tree[Integer], MulSym[repr], ExpSym[repr])) => Either[ErrMsg, repr[Integer]])(args: (Tree[Integer], MulSym[repr], ExpSym[repr])): Either[ErrMsg, repr[Integer]] = args match {
        //     case (x, s1, s2) => x match {
        //       case Node("Mul", Seq(x, y)) => for {
        //         a <- self((x, s1, s2)).right
        //         b <- self((y, s1, s2)).right
        //       } yield s1.mul(a)(b)
        //       case x => fromTree_((x: (Tree[Integer], ExpSym[repr])) => self(x._1, s1, x._2))((x, s2))
        //     }
        //   }
        //   def fromTreeExt[repr[_]](x: Tree[Integer])(implicit s1: MulSym[repr], s2: ExpSym[repr]): Either[ErrMsg, repr[Integer]] = Fix(fromTreeExt_[repr])((x, s1, s2))
        // }

        // }}}

      }

      // }}}

      object Use {
        import ExpSym._
        // deserialization problem is the problem that we're unable to have a truly
        // generic representation (while allowing for language extensibility). the
        // symptom is having to invoke 'fromTree' twice for two uses. if the
        // language were fixed, what we could do is to create a wrapper
        // representation incorporating all the relevant typeclasses (language
        // constructs). what the duplicating interpretor does is to have these
        // trampoline-like constructs which lazily (per use) invoke, in essence, the
        // fromTree translation (via the ExpSym instance for the 'repr' pair)
        val result = ClosedRecursion.fromTree[Debug](tf1_tree).right.map(view)
        val result2 = OpenRecursion.fromTree[Debug](tf1_tree).right.map(view)
        // def tf1_tree_parse[repr[_]] = OpenRecursion.Poly2.fromTree[repr](tf1_tree)
        // def result2a[repr[_]](implicit s1: ExpSym[repr]): Either[ErrMsg, repr[Integer]] = {
        //   OpenRecursion.Poly.fromTree[repr](tf1_tree)
        //   // tf1_tree_parse[repr].right.map(_(s1))
        // }
        //val result2b = result2a[Debug].right.map(view)

        val result3 = ClosedRecursion.fromTreeExt[Debug](tfm1_tree).right.map(view)
        val result4 = OpenRecursion.fromTreeExt[Debug](tfm1_tree).right.map(view)
        // def result4a[repr[_]](implicit s1: ExpSym[repr], s2: MulSym[repr]) = {
        //   OpenRecursion.Poly.fromTreeExt[repr](tfm1_tree)
        // }
        //val result4b = result4a[Debug].right.map(view)

        // {{{ TODO: duplicating interpreter

        // import ExpSym._
        // // this opens up the door to nested pairs. so we basically have a type-level
        // // list-like structure corresponding to the various sequence of operations
        // // that we would like to do. then you can call fromTree[(A, (B, (C, D)))]
        // // which in one go (and in haskell, lazily) does the requisite mapping to A,
        // // B, C, and D types
        // implicit class ExpSym_Dup[R1, R2](val x: (R1, R2))(implicit s1: ExpSym[R1], s2: ExpSym[R2]) extends ExpSym[(R1, R2)] {
        //   def lit = (x: Integer) => massageTuple(s1.lit(x), s2.lit(x))
        //   def neg = (x: (Integer, (R1, R2))) => massageTuple(s1.neg((x._1, x._2._1)), s2.neg((x._1, x._2._2)))
        //   def add = (x: (Integer, (R1, R2))) => (y: (Integer, (R1, R2))) =>
        //     massageTuple(s1.add((x._1, x._2._1))((y._1, y._2._1)), s2.add((x._1, x._2._2))((y._1, y._2._2)))
        // }
        // def massageTuple[T, R1, R2](x: (T, R1), y: (T, R2)) = (y._1, (x._2, y._2))

        // def check_consume[A, B](f: A => B)(x: Either[ErrMsg, A]) = x match {
        //   case Left(e) => println(s"Error: ${e}")
        //   case Right(x) => f(x)
        // }
        // def dup_consume[A, B](f: A => Any)(x: ExpSym_Dup[A, B]): B = {
        //   println(f(x.x._1))
        //   x.x._2
        // }

        // }}}
      }
    }

    // }}}
  }

}