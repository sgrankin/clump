package clump

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import com.twitter.util.Await
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import com.twitter.util.Future
import org.mockito.Mockito._

@RunWith(classOf[JUnitRunner])
class ClumpApiSpec extends Spec {

  "the Clump object" >> {

    "allows to create a constant clump" >> {

      "from a future (Clump.future)" >> {

        "success" in {
          clumpResult(Clump.future(Future.value(Some(1)))) mustEqual Some(1)
        }

        "failure" in {
          clumpResult(Clump.future(Future.exception(new IllegalStateException))) must throwA[IllegalStateException]
        }
      }

      "from a value (Clump.value)" in {
        clumpResult(Clump.value(1)) mustEqual Some(1)
      }

      "from an option (Clump.value)" >> {

        "defined" in {
          clumpResult(Clump.value(Option(1))) mustEqual Option(1)
        }

        "empty" in {
          clumpResult(Clump.value(None)) mustEqual None
        }
      }

      "failed (Clump.exception)" in {
        clumpResult(Clump.exception(new IllegalStateException)) must throwA[IllegalStateException]
      }
    }

    "allows to create a clump traversing multiple inputs (Clump.traverse)" in {
      val inputs = List(1, 2, 3)
      val clump = Clump.traverse(inputs)(i => Clump.value(i + 1))
      clumpResult(clump) mustEqual Some(List(2, 3, 4))
    }

    "allows to collect multiple clumps in only one (Clump.collect)" in {
      val clumps = List(Clump.value(1), Clump.value(2))
      clumpResult(Clump.collect(clumps)) mustEqual Some(List(1, 2))
    }

    "allows to create a clump source (Clump.sourceFrom)" in {
      def fetch(inputs: Set[Int]) = Future.value(inputs.map(i => i -> i.toString).toMap)
      val source = Clump.sourceFrom(fetch, 2)
      clumpResult(source.get(1)) mustEqual Some("1")
    }

    "allows to create a clump source with key function (Clump.source)" in {
      def fetch(inputs: Set[Int]) = Future.value(inputs.map(_.toString))
      val source = Clump.source(fetch)(_.toInt)
      clumpResult(source.get(1)) mustEqual Some("1")
    }

    "allows to create a clump source with zip as the key function (Clump.sourceZip)" in {
      def fetch(inputs: List[Int]) = Future.value(inputs.map(_.toString))
      val source = Clump.sourceZip(fetch)
      clumpResult(source.get(1)) mustEqual Some("1")
    }

    "allows to create an empty Clump (Clump.none)" in {
      clumpResult(Clump.None) ==== None
    }
  }

  "a Clump instance" >> {

    "can be mapped to a new clump" >> {

      "using simple a value transformation (clump.map)" in {
        clumpResult(Clump.value(1).map(_ + 1)) mustEqual Some(2)
      }

      "using a transformation that creates a new clump (clump.flatMap)" >> {
        "both clumps are defined" in {
          clumpResult(Clump.value(1).flatMap(i => Clump.value(i + 1))) mustEqual Some(2)
        }
        "initial clump is undefined" in {
          clumpResult(Clump.value(None).flatMap(i => Clump.value(2))) mustEqual None
        }
      }
    }

    "can be joined with another clump and produce a new clump with the value of both (clump.join)" >> {
      "both clumps are defined" in {
        clumpResult(Clump.value(1).join(Clump.value(2))) mustEqual Some(1, 2)
      }
      "one of them is undefined" in {
        clumpResult(Clump.value(1).join(Clump.value(None))) mustEqual None
      }
    }

    "allows to recover from failures" >> {

      "using a function that recovers using a new value (clump.handle)" in {
        val clump =
          Clump.exception(new IllegalStateException).handle {
            case e: IllegalStateException => 2
          }
        clumpResult(clump) mustEqual Some(2)
      }

      "using a function that recovers the failure using a new clump (clump.rescue)" >> {
        "exception happens" in {
          val clump =
            Clump.exception(new IllegalStateException).rescue {
              case e: IllegalStateException => Clump.value(2)
            }
          clumpResult(clump) mustEqual Some(2)
        }
        "exception doesn't happen" in {
          val clump =
            Clump.value(1).rescue {
              case e: IllegalStateException => Clump.value(None)
            }
          clumpResult(clump) mustEqual Some(1)
        }
      }
    }

    "can have its result filtered (clump.withFilter)" in {
      clumpResult(Clump.value(1).withFilter(_ != 1)) mustEqual None
      clumpResult(Clump.value(1).withFilter(_ == 1)) mustEqual Some(1)
    }

    "uses a covariant type parameter" in {
      trait A
      class B extends A
      class C extends A
      val clump = Clump.traverse(List(new B, new C))(Clump.value(_))
      (clump: Clump[List[A]]) must beAnInstanceOf[Clump[List[A]]]
    }

    "result can never be None after clump.orElse" in {
      clumpResult(Clump.None.orElse(1)) ==== Some(1)
    }

    "can represent its result as a list (clump.list) when its type is List[T]" in {
      Await.result(Clump.value(List(1, 2)).list) ==== List(1, 2)
      // Clump.value(1).list doesn't compile
    }

    "can provide a result falling back to a default (clump.getOrElse)" >> {
      "initial clump is undefined" in {
        Await.result(Clump.value(None).getOrElse(1)) ==== 1
      }

      "initial clump is defined" in {
        Await.result(Clump.value(Some(2)).getOrElse(1)) ==== 2
      }
    }

    "has a utility method (clump.apply) for unwrapping optional result" in {
      Clump.value(1).apply() ==== 1

      try {
        Clump.None.apply()
        ko("expected NoSuchElementException to be thrown")
      } catch {
        case e: NoSuchElementException => ok
        case e: Throwable              => ko(s"expected NoSuchElementException but was $e")
      }
    }
  }
}