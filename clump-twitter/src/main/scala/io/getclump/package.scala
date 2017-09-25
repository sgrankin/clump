package io

package object getclump {

  // Execution contexts are not used by twitter-util so this allows you not to have to specify it
  private[getclump] implicit val ec = scala.concurrent.ExecutionContext.global

  private[getclump] type Try[+T] = com.twitter.util.Try[T]
  private[getclump] val Try     = com.twitter.util.Try
  private[getclump] val Success = com.twitter.util.Return
  private[getclump] val Failure = com.twitter.util.Throw

  private[getclump] type Promise[T] = com.twitter.util.Promise[T]
  private[getclump] val Promise = com.twitter.util.Promise

  private[getclump] type Future[+T] = com.twitter.util.Future[T]
  private[getclump] val Future = com.twitter.util.Future

  implicit class PromiseBridge[T](val promise: Promise[T]) extends AnyVal {
    def complete(result: com.twitter.util.Try[T])           = promise.update(result)
    def tryCompleteWith(future: com.twitter.util.Future[T]) = { promise.become(future); promise }
  }

  implicit class FutureBridge[T](val future: Future[T]) extends AnyVal {
    def recover[U >: T](pf: PartialFunction[Throwable, U])             = future.handle(pf)
    def recoverWith[U >: T](pf: PartialFunction[Throwable, Future[U]]) = future.rescue(pf)
    def zip[U](that: Future[U])                                        = future.join(that)
    def isCompleted                                                    = future.poll.isDefined
    def onComplete[U](f: com.twitter.util.Try[T] => U)                 = future.liftToTry.map(f)
  }

  implicit class FutureCompanionBridge(val companion: Future.type) extends AnyVal {
    def successful[T](value: T)                   = Future.value(value)
    def failed[T](exception: Throwable)           = Future.exception[T](exception)
    def sequence[T](futures: Iterable[Future[T]]) = Future.collect(futures.toSeq).map(_.toList)
  }

  private[getclump] def awaitResult[T](future: Future[T]) =
    com.twitter.util.Await.result(future)
}
