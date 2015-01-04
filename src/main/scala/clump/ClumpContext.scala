package clump

import com.twitter.util.Local
import scala.collection.mutable.HashMap
import scala.collection.mutable.SynchronizedMap
import com.twitter.util.Future
import com.twitter.util.JavaTimer
import com.twitter.util.TimeConversions._

final class ClumpContext {

  private val fetchers =
    new HashMap[ClumpSource[_, _], ClumpFetcher[_, _]]()

  def fetcherFor[T, U](source: ClumpSource[T, U]) =
    synchronized {
      fetchers
        .getOrElseUpdate(source, new ClumpFetcher(source))
        .asInstanceOf[ClumpFetcher[T, U]]
    }

  def flush(clumps: List[Clump[_]]): Future[Unit] =
    clumps match {
      case Nil => Future.Unit
      case clumps =>
        flushUpstream(clumps).flatMap { _ =>
          flushDownstream(clumps).flatMap { _ =>
            flushFetchers
          }
        }
    }

  private def flushUpstream(clumps: List[Clump[_]]) =
    flush(clumps.map(_.upstream).flatten)

  private def flushDownstream(clumps: List[Clump[_]]) =
    Future.collect(clumps.map(_.downstream)).flatMap { down =>
      flush(down.flatten.toList)
    }

  private def flushFetchers =
    Future.collect(fetchers.values.map(_.flush).toList).unit
}

object ClumpContext {

  private val local = new Local[ClumpContext]

  def apply() =
    local().getOrElse {
      val context = new ClumpContext
      local.set(Some(context))
      context
    }
}
