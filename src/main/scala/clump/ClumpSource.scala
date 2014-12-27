package clump

import com.twitter.util.Future

class ClumpSource[T, U](val fetch: Set[T] => Future[Map[T, U]], val maxBatchSize: Int) {

  def this(fetch: Set[T] => Future[Iterable[U]], keyFn: U => T, maxBatchSize: Int) = {
    this(fetch.andThen(_.map(_.map(v => (keyFn(v), v)).toMap)), maxBatchSize)
  }

  def list(inputs: T*): Clump[List[U]] =
    list(inputs.toList)

  def list(inputs: List[T]): Clump[List[U]] =
    Clump.collect(inputs.map(get))

  def get(input: T): Clump[U] =
    new ClumpFetch(input, ClumpContext().fetcherFor(this))
  
  def getOrElse(input: T, default: => U) =
    get(input).orElse(default)

  def apply(input: T): Clump[U] =
    get(input).orElse(throw new NoSuchElementException)
}

object ClumpSource {
  def zip[T, U](fetch: List[T] => Future[List[U]], maxBatchSize: Int): ClumpSource[T, U] = {
    val zip: List[T] => Future[Map[T, U]] = { inputs =>
      fetch(inputs).map(inputs.zip(_).toMap)
    }
    val setToList: Set[T] => List[T] = _.toList
    new ClumpSource(setToList.andThen(zip), maxBatchSize)
  }
}