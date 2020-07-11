// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package simulation

import cats._
import cats.effect._
import cats.effect.concurrent.Ref
import cats.free.Free
import cats.implicits._
import scala.annotation.tailrec
import scala.collection.immutable.Queue
import skunk.exception.ProtocolError
import skunk.net.message.BackendMessage
import skunk.net.message.FrontendMessage
import skunk.net.MessageSocket
import skunk.util.Origin
import skunk.net.message.ErrorResponse

object SimMessageSocket {

  // Monadic DSL for writing a simulated Postgres server. We're using an explicit Yoneda encoding
  // here so we can define a functor instance below, which is necessary if we want to run programs
  // step by step (which we do).
  sealed trait Step[+A]
  case class Send[A](m: BackendMessage, k: Unit => A) extends Step[A]
  case class Expect[A](h: PartialFunction[FrontendMessage, A]) extends Step[A]
  object Step {
    implicit val FunctorStep: Functor[Step] =
      new Functor[Step] {
        def map[A,B](fa: Step[A])(f: A => B): Step[B] =
          fa match {
            case Send(m, k) => Send(m, k andThen f)
            case Expect(h)    => Expect(h andThen f)
          }
      }
  }

  // To ensure that simulations all terminate cleanly, we will provide a value that must be yielded
  // but has no public constructor, so you can only construct it with `halt` below.
  sealed trait Halt
  private object Halt extends Halt

  // A postgres simulator has this type.
  type Simulator = Free[Step, Halt]

  // Smart constructors for our DSL.
  trait DSL {
    type Simulator = SimMessageSocket.Simulator
    def send(m: BackendMessage): Free[Step, Unit] = Free.liftF(Send(m, identity))
    def expect[A](h: PartialFunction[FrontendMessage, A]): Free[Step, A] = Free.liftF(Expect(h))
    def flatExpect[A](h: PartialFunction[FrontendMessage, Free[Step, A]]): Free[Step, A] = expect(h).flatten
    def error(msg: String): Free[Step, Unit] = send(ErrorResponse(Map('M' -> msg, 'S' -> "SIMULATOR_ERROR", 'C' -> "0")))
    def halt: Free[Step, Halt] = expect { case _ => Halt } // not obvious
  }

  // Our simulated runtime consists of a queue of outgoing messages, plus a continuation that consumes
  // an incoming message and computes the next continuation.
  case class SimState(queue: Queue[BackendMessage], k: FrontendMessage => Free[Step, _]) {

    // To receive a message we dequeue from our state. Because `advance` above enqueues eagerly it is
    // impossible to miss messages. If there are no pending messages we're stuck.
    def receive: Either[String, (BackendMessage, SimState)] =
      queue.dequeueOption match {
        case Some((m, q)) => (m, copy(queue = q)).asRight
        case None         => "No pending messages.".asLeft
      }

    // To send a message we pass it to the continuation and compute the next one.
    def send(m: FrontendMessage): Either[String, SimState] =
      SimState.advance(k(m), queue)

  }
  object SimState {

    // In principle it's possible to construct a simulator that's already halted, but in practice
    // it's impossible. Scala doesn't know this though, so we handle the case here.
    def initial(simulator: Simulator): Either[String, SimState] =
      advance(simulator, Queue.empty)

    // Initially and after we process a message we want to turn the crank until we're sitting on
    // an Expect node. This ensures that all the pending outgoing messages are queued up so they
    // can be read, and the simulator is ready to accept the next message. Of course if the
    // simulator has terminated we're stuck.
    @tailrec private def advance(m: Free[Step, _], q: Queue[BackendMessage]): Either[String, SimState] =
      m.resume match {
        case Left(Send(m, k)) => advance(k(()), q :+ m)
        case Left(Expect(h))     => SimState(q, h).asRight
        case Right(_)            => "The simulation has ended.".asLeft
      }

  }

  // Ok so now we have what we need to construct a MessageSocket whose soul is a simulation defined
  // with the DSL above. We'll advance the machine first, ensuring that there's at least one
  // Expect node inside, otherwise we're done early. This will never happen because users aren't
  // able to construct a value of type `Halt` so it's kind of academic. Anyway we'll stuff our
  // initial SimState into a Ref and then we consult it when we `send` and `receive`, using the
  // standard ref-state-machine pattern. This is pretty cool really.
  def apply(simulaton: Simulator): IO[MessageSocket[IO]] =
    SimState.initial(simulaton).leftMap(new IllegalStateException(_)).liftTo[IO].flatMap(Ref[IO].of).map { ref =>
      new MessageSocket[IO] {

        def receive: IO[BackendMessage] =
          ref.modify { ms =>
            ms.receive match {
              case Right((m, ms)) => (ms, m.pure[IO])
              case Left(err)      => (ms, IO.raiseError(new IllegalStateException(err)))
            }
          } .flatten

        def send(message: FrontendMessage): IO[Unit] =
          ref.modify { ms =>
            ms.send(message) match {
              case Right(ms) => (ms, IO.unit)
              case Left(err) => (ms, IO.raiseError(new IllegalStateException(err)))
            }
          } .flatten

        def history(max: Int): IO[List[Either[Any,Any]]] =
          Nil.pure[IO] // not implemeneted

        def expect[B](f: PartialFunction[BackendMessage,B])(implicit or: Origin): IO[B] =
          receive.flatMap { msg =>
            f.lift(msg) match {
              case Some(b) => b.pure[IO]
              case None    =>  IO.raiseError(new ProtocolError(msg, or))
            }
          }

        def flatExpect[B](f: PartialFunction[BackendMessage,IO[B]])(implicit or: Origin): IO[B] =
          expect(f).flatten

      }
    }

}