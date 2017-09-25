package io.getclump

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.concurrent.ExecutionContext.Implicits.global

trait Spec extends Specification with Mockito {

  protected def clumpResult[T](clump: Clump[T]) =
    awaitResult(clump.get)
}
