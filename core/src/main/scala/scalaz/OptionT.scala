package scalaz

import std.option.{optionInstance, none, some}

/**
 * OptionT monad transformer.
 */
final case class OptionT[F[+_], +A](run: F[Option[A]]) {
  self =>

  def map[B](f: A => B)(implicit F: Functor[F]): OptionT[F, B] = new OptionT[F, B](mapO(_ map f))

  def foreach(f: A => Unit)(implicit E: Each[F]): Unit = E.each(run)(_ foreach f)

  def flatMap[B](f: A => OptionT[F, B])(implicit F: Monad[F]): OptionT[F, B] = new OptionT[F, B](
    F.bind(self.run) {
      case None    => F.point(None: Option[B])
      case Some(z) => f(z).run
    }
  )

  def flatMapF[B](f: A => F[B])(implicit F: Monad[F]): OptionT[F, B] = new OptionT[F, B](
    F.bind(self.run) {
      case None    => F.point(none[B])
      case Some(z) => F.map(f(z))(b => some(b))
    }
  )

  def foldRight[Z](z: => Z)(f: (A, => Z) => Z)(implicit F: Foldable[F]): Z = {
    import std.option._
    F.foldRight[Option[A], Z](run, z)((a, b) => Foldable[Option].foldRight[A, Z](a, b)(f))
  }

  def traverse[G[_], B](f: A => G[B])(implicit F: Traverse[F], G: Applicative[G]): G[OptionT[F, B]] = {
    import std.option._
    G.map(F.traverse(run)(o => Traverse[Option].traverse(o)(f)))(OptionT(_))
  }

  /** @see `app` */
  @deprecated("Monad[F] will be required, and this fixed, in 7.1; use a different `Apply` combinator in 7.0.x",
              "7.0.5")
  def ap[B](f: => OptionT[F, A => B])(implicit F: Apply[F]): OptionT[F, B] =
    app(f)

  /** Apply a function in the environment of both options, containing
    * both `F`s.  It is not compatible with `Monad#bind`.
    */
  def app[B](f: => OptionT[F, A => B])(implicit F: Apply[F]): OptionT[F, B] =
    OptionT(F.apply2(f.run, run) {
      case (ff, aa) => optionInstance.ap(aa)(ff)
    })

  def isDefined(implicit F: Functor[F]): F[Boolean] = mapO(_.isDefined)

  def isEmpty(implicit F: Functor[F]): F[Boolean] = mapO(_.isEmpty)

  def filter(f: A => Boolean)(implicit F: Functor[F]): OptionT[F, A] = OptionT(F.map(self.run) { _ filter f })

  def fold[X](some: A => X, none: => X)(implicit F: Functor[F]): F[X] =
    mapO {
      case None => none
      case Some(a) => some(a)
    }

  def getOrElse[AA >: A](default: => AA)(implicit F: Functor[F]): F[AA] = mapO(_.getOrElse(default))

  /** Alias for `getOrElse`. */
  def |[AA >: A](default: => AA)(implicit F: Functor[F]): F[AA] = getOrElse(default)

  def getOrElseF[AA >: A](default: => F[AA])(implicit F: Monad[F]): F[AA] =
    F.bind(self.run) {
      case None => default
      case Some(a) => F.point(a)
    }

  def exists(f: A => Boolean)(implicit F: Functor[F]): F[Boolean] = mapO(_.exists(f))

  def forall(f: A => Boolean)(implicit F: Functor[F]): F[Boolean] = mapO(_.forall(f))

  def orElse[AA >: A](a: => OptionT[F, AA])(implicit F: Monad[F]): OptionT[F, AA] =
    OptionT(F.bind(run) {
      case None => a.run
      case x@Some(_) => F.point(x)
    })

  def |||[AA >: A](a: => OptionT[F, AA])(implicit F: Monad[F]): OptionT[F, AA] =
    orElse(a)

  /** @since 7.0.3 */
  def toRight[E](e: => E)(implicit F: Functor[F]): EitherT[F,E,A] = EitherT(F.map(run)(std.option.toRight(_)(e)))

  /** @since 7.0.3 */
  def toLeft[B](b: => B)(implicit F: Functor[F]): EitherT[F,A,B] = EitherT(F.map(run)(std.option.toLeft(_)(b)))

  private def mapO[B](f: Option[A] => B)(implicit F: Functor[F]) = F.map(run)(f)
}

//
// Prioritized Implicits for type class instances
//

trait OptionTInstances3 {
  implicit def optionTFunctor[F[+_]](implicit F0: Functor[F]): Functor[({type λ[α] = OptionT[F, α]})#λ] = new OptionTFunctor[F] {
    implicit def F: Functor[F] = F0
  }
}

trait OptionTInstances2 extends OptionTInstances3 {
  @deprecated("Monad[F] will be required, and this instance removed, in 7.1",
              "7.0.5")
  def optionTApply[F[+_]](implicit F0: Apply[F]): Apply[({type λ[α] = OptionT[F, α]})#λ] = new OptionTApply[F] {
    implicit def F: Apply[F] = F0
  }
}

trait OptionTInstances1 extends OptionTInstances2 {
  implicit def optionTFoldable[F[+_]](implicit F0: Foldable[F]): Foldable[({type λ[α] = OptionT[F, α]})#λ] = new OptionTFoldable[F] {
    implicit def F: Foldable[F] = F0
  }
  @deprecated("Monad[F] will be required, and this instance removed, in 7.1",
              "7.0.5")
  def optionTApplicative[F[+_]](implicit F0: Applicative[F]): Applicative[({type λ[α] = OptionT[F, α]})#λ] = new OptionTApplicative[F] {
    implicit def F: Applicative[F] = F0
  }
}

trait OptionTInstances0 extends OptionTInstances1 {
  implicit def optionTMonadPlus[F[+_]](implicit F0: Monad[F]): MonadPlus[({type λ[α] = OptionT[F, α]})#λ] = new OptionTMonadPlus[F] {
    implicit def F: Monad[F] = F0
  }
}

trait OptionTInstances extends OptionTInstances0 {
  implicit def optionTMonadTrans: Hoist[OptionT] = new OptionTHoist {}

  implicit def optionTTraverse[F[+_]](implicit F0: Traverse[F]): Traverse[({type λ[α] = OptionT[F, α]})#λ] = new OptionTTraverse[F] {
    implicit def F: Traverse[F] = F0
  }

  implicit def optionTEqual[F[+_], A](implicit F0: Equal[F[Option[A]]]): Equal[OptionT[F, A]] = F0.contramap((_: OptionT[F, A]).run)
}

trait OptionTFunctions {
  def optionT[M[+_]] = new (({type λ[α] = M[Option[α]]})#λ ~> ({type λ[α] = OptionT[M, α]})#λ) {
    def apply[A](a: M[Option[A]]) = new OptionT[M, A](a)
  }

  def monadTell[F[+_, +_], W, A](implicit MT0: MonadTell[F, W]) = new OptionTMonadTell[F, W] {
    def MT = MT0
  }

  def monadListen[F[+_, +_], W, A](implicit ML0: MonadListen[F, W]) = new OptionTMonadListen[F, W] {
    def MT = ML0
  }
}

object OptionT extends OptionTFunctions with OptionTInstances {
  def some[M[+_], A](v: => A)(implicit M: Applicative[M]): OptionT[M, A] =
    OptionT.optionT[M].apply[A](M.point(Some(v)))

  def none[M[+_], A](implicit M: Applicative[M]): OptionT[M, A] =
    OptionT.optionT[M].apply[A](M.point(None))

  implicit def optionTShow[F[+_], A](implicit F0: Show[F[Option[A]]]): Show[OptionT[F, A]] = Contravariant[Show].contramap(F0)(_.run)
}

//
// Implementation traits for type class instances
//

private[scalaz] trait OptionTFunctor[F[+_]] extends Functor[({type λ[α] = OptionT[F, α]})#λ] {
  implicit def F: Functor[F]

  override def map[A, B](fa: OptionT[F, A])(f: A => B): OptionT[F, B] = fa map f
}

private[scalaz] trait OptionTApply[F[+_]] extends Apply[({type λ[α] = OptionT[F, α]})#λ] with OptionTFunctor[F] {
  implicit def F: Apply[F]

  override def ap[A, B](fa: => OptionT[F, A])(f: => OptionT[F, A => B]): OptionT[F, B] = fa app f
}

private[scalaz] trait OptionTApplicative[F[+_]] extends Applicative[({type λ[α] = OptionT[F, α]})#λ] with OptionTApply[F] {
  implicit def F: Applicative[F]

  def point[A](a: => A): OptionT[F, A] = OptionT[F, A](F.point(some(a)))
}

private[scalaz] trait OptionTMonad[F[+_]] extends Monad[({type λ[α] = OptionT[F, α]})#λ] with OptionTApplicative[F] {
  implicit def F: Monad[F]

  override def ap[A, B](fa: => OptionT[F, A])(f: => OptionT[F, A => B]): OptionT[F, B] =
    OptionT(F.bind(f.run){
              case None => F.point(None)
              case Some(ff) => F.map(fa.run)(_ map ff)
            })

  def bind[A, B](fa: OptionT[F, A])(f: A => OptionT[F, B]): OptionT[F, B] = fa flatMap f

}

private[scalaz] trait OptionTFoldable[F[+_]] extends Foldable.FromFoldr[({type λ[α] = OptionT[F, α]})#λ] {
  implicit def F: Foldable[F]

  override def foldRight[A, B](fa: OptionT[F, A], z: => B)(f: (A, => B) => B): B = fa.foldRight(z)(f)
}

private[scalaz] trait OptionTTraverse[F[+_]] extends Traverse[({type λ[α] = OptionT[F, α]})#λ] with OptionTFoldable[F] with OptionTFunctor[F]{
  implicit def F: Traverse[F]

  def traverseImpl[G[_] : Applicative, A, B](fa: OptionT[F, A])(f: A => G[B]): G[OptionT[F, B]] = fa traverse f
}

private[scalaz] trait OptionTHoist extends Hoist[OptionT] {
  def liftM[G[+_], A](a: G[A])(implicit G: Monad[G]): OptionT[G, A] =
    OptionT[G, A](G.map[A, Option[A]](a)((a: A) => some(a)))

  def hoist[M[+_]: Monad, N[+_]](f: M ~> N) = new (({type f[x] = OptionT[M, x]})#f ~> ({type f[x] = OptionT[N, x]})#f) {
    def apply[A](fa: OptionT[M, A]): OptionT[N, A] = OptionT(f.apply(fa.run))
  }

  implicit def apply[G[+_] : Monad]: Monad[({type λ[α] = OptionT[G, α]})#λ] = OptionT.optionTMonadPlus[G]
}

private[scalaz] trait OptionTMonadPlus[F[+_]] extends MonadPlus[({type λ[α] = OptionT[F, α]})#λ] with OptionTMonad[F] {
  implicit def F: Monad[F]

  def empty[A]: OptionT[F, A] = OptionT(F point none[A])
  def plus[A](a: OptionT[F, A], b: => OptionT[F, A]): OptionT[F, A] = a orElse b
}

private[scalaz] trait OptionTMonadTell[F[+_, +_], W] extends MonadTell[({ type λ[α, +β] = OptionT[({ type f[+x] = F[α, x] })#f, β] })#λ, W] with OptionTMonad[({ type λ[+α] = F[W, α] })#λ] with OptionTHoist {
  def MT: MonadTell[F, W]

  implicit def F = MT

  def writer[A](w: W, v: A): OptionT[({ type λ[+α] = F[W, α] })#λ, A] =
    liftM[({ type λ[+α] = F[W, α] })#λ, A](MT.writer(w, v))

  def some[A](v: => A): OptionT[({ type λ[+α] = F[W, α] })#λ, A] =
    OptionT.optionT[({ type λ[+α] = F[W, α] })#λ].apply[A](MT.point(Some(v)))

  def none[A]: OptionT[({ type λ[+α] = F[W, α] })#λ, A] =
    OptionT.optionT[({ type λ[+α] = F[W, α] })#λ].apply[A](MT.point(None))
}

private[scalaz] trait OptionTMonadListen[F[+_, +_], W] extends MonadListen[({ type λ[α, β] = OptionT[({ type f[+x] = F[α, x] })#f, β] })#λ, W] with OptionTMonadTell[F, W] {
  def MT: MonadListen[F, W]

  def listen[A](ma: OptionT[({ type λ[+α] = F[W, α] })#λ, A]): OptionT[({ type λ[+α] = F[W, α] })#λ, (A, W)] = {
    val tmp = MT.bind[(Option[A], W), Option[(A, W)]](MT.listen(ma.run)) {
      case (None, _) => MT.point(None)
      case (Some(a), w) => MT.point(Some(a, w))
    }

    OptionT.optionT[({ type λ[+α] = F[W, α] })#λ].apply[(A, W)](tmp)
  }
}
