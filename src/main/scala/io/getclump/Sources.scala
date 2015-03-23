package io.getclump

import com.twitter.util.Future

import scala.collection.generic.CanBuildFrom

protected[getclump] trait Sources extends Tuples {

  def source[KS, V, K](fetch: KS => Future[Iterable[V]])(keyExtractor: V => K)
                      (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[K, V] =
    new ClumpSource(FunctionIdentity(fetch), extractKeys(adaptInput(fetch), keyExtractor))

  def source[A, KS, V, K](fetch: (A, KS) => Future[Iterable[V]])(keyExtractor: V => K)
                         (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parameterizeFetch(normalize1, denormalize1[A, K], fetch1(fetch), keyExtractor))

  def source[A, B, KS, V, K](fetch: (A, B, KS) => Future[Iterable[V]])(keyExtractor: V => K)
                            (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, B, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parameterizeFetch(normalize2, denormalize2[A, B, K], fetch2(fetch), keyExtractor))

  def source[A, B, C, KS, V, K](fetch: (A, B, C, KS) => Future[Iterable[V]])(keyExtractor: V => K)
                               (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, B, C, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parameterizeFetch(normalize3, denormalize3[A, B, C, K], fetch3(fetch), keyExtractor))

  def source[A, B, C, D, KS, V, K](fetch: (A, B, C, D, KS) => Future[Iterable[V]])(keyExtractor: V => K)
                                  (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, B, C, D, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parameterizeFetch(normalize4, denormalize4[A, B, C, D, K], fetch4(fetch), keyExtractor))

  def source[KS, K, V](fetch: KS => Future[Map[K, V]])
                      (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[K, V] =
    new ClumpSource(FunctionIdentity(fetch), adaptOutput(adaptInput(fetch)))

  def source[A, KS, K, V](fetch: (A, KS) => Future[Map[K, V]])
                         (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parameterizeFetch(normalize1, denormalize1[A, K], fetch1(fetch)))

  def source[A, B, KS, K, V](fetch: (A, B, KS) => Future[Map[K, V]])
                            (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, B, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parameterizeFetch(normalize2, denormalize2[A, B, K], fetch2(fetch)))

  def source[A, B, C, KS, K, V](fetch: (A, B, C, KS) => Future[Map[K, V]])
                               (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, B, C, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parameterizeFetch(normalize3, denormalize3[A, B, C, K], fetch3(fetch)))

  def source[A, B, C, D, KS, K, V](fetch: (A, B, C, D, KS) => Future[Map[K, V]])
                                  (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, B, C, D, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parameterizeFetch(normalize4, denormalize4[A, B, C, D, K], fetch4(fetch)))

  def sourceZip[K, V](fetch: List[K] => Future[List[V]]): ClumpSource[K, V] =
    new ClumpSource(FunctionIdentity(fetch), zipped(fetch))

  def sourceZip[A, K, V](fetch: (A, List[K]) => Future[List[V]]): ClumpSource[(A, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parameterizeFetchZip(normalize1, fetch1(fetch)))

  def sourceZip[A, B, K, V](fetch: (A, B, List[K]) => Future[List[V]]): ClumpSource[(A, B, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parameterizeFetchZip(normalize2, fetch2(fetch)))

  def sourceZip[A, B, C, K, V](fetch: (A, B, C, List[K]) => Future[List[V]]): ClumpSource[(A, B, C, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parameterizeFetchZip(normalize3, fetch3(fetch)))

  def sourceZip[A, B, C, D, K, V](fetch: (A, B, C, D, List[K]) => Future[List[V]]): ClumpSource[(A, B, C, D, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parameterizeFetchZip(normalize4, fetch4(fetch)))

  private[this] def parameterizeFetch[I, P, O, T, C](normalize: I => (P, T), denormalize: (P, T) => I, fetch: (P, C) => Future[Iterable[O]], extractKey: O => T)
                                                    (implicit cbf: CanBuildFrom[Nothing, T, C]): Set[I] => Future[Map[I, O]] =
    parameterizeFetch[I, P, O, T, C](normalize, denormalize, fetch = (params: P, coll: C) => fetch(params, coll).map(_.map(v => extractKey(v) -> v).toMap))

  private[this] def parameterizeFetch[I, P, O, T, C](normalize: I => (P, T), denormalize: (P, T) => I, fetch: (P, C) => Future[Iterable[(T, O)]])
                                                    (implicit cbf: CanBuildFrom[Nothing, T, C]): Set[I] => Future[Map[I, O]] =
    (inputs: Set[I]) => {
      val futures =
        inputs.map(normalize).groupBy { case (params, _) => params }.map {
          case (params, paramsAndKeys) =>
            fetch(params, cbf.apply().++=(paramsAndKeys.map { case (_, keys) => keys }).result()).map {
              _.map { case (key, value) => denormalize(params, key) -> value }
            }
        }.toSeq
      Future.collect(futures).map(_.reduce(_ ++ _).toMap)
    }

  private[this] def parameterizeFetchZip[I, P, O, T](normalize: I => (P, T), fetch: (P, List[T]) => Future[Iterable[O]]): Set[I] => Future[Map[I, O]] =
    (inputs: Set[I]) => {
      val listInputs = inputs.toList
      val futures =
        listInputs.map(normalize).groupBy { case (params, _) => params }.map {
          case (params, paramsAndKeys) =>
            fetch(params, paramsAndKeys.map { case (_, keys) => keys })
        }.toSeq
      val listOutputs = Future.collect(futures).map(_.reduce(_ ++ _)).map(_.toList)
      listOutputs.map(listInputs.zip(_).toMap)
    }

  private[this] def zipped[T, U](fetch: List[T] => Future[List[U]]) = {
    val zip: List[T] => Future[Map[T, U]] = { inputs =>
      fetch(inputs).map(inputs.zip(_).toMap)
    }
    val setToList: Set[T] => List[T] = _.toList
    setToList.andThen(zip)
  }

  private[this] def extractKeys[T, U](fetch: Set[T] => Future[Iterable[U]], keyExtractor: U => T) =
    fetch.andThen(_.map(resultsToKeys(keyExtractor, _)))

  private[this] def resultsToKeys[U, T](keyExtractor: (U) => T, results: Iterable[U]) =
    results.map(v => (keyExtractor(v), v)).toMap

  private[this] def adaptInput[T, C, R](fetch: C => Future[R])(implicit cbf: CanBuildFrom[Nothing, T, C]) =
    (c: Set[T]) => fetch(cbf.apply().++=(c).result())

  private[this] def adaptOutput[T, U, C](fetch: C => Future[Iterable[(T, U)]]) =
    fetch.andThen(_.map(_.toMap))

}