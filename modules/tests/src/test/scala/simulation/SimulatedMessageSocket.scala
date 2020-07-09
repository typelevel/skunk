// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package simulation

import cats._
import cats.implicits._
import skunk.net.message.FrontendMessage
import cats.free.Free
import scala.collection.immutable.Queue
import skunk.net.MessageSocket
import skunk.net.message.BackendMessage
import cats.effect._
import cats.effect.concurrent.Ref
import skunk.util.Origin
import skunk.exception.ProtocolError

object SimulatedMessageSocket {

  // Monadic DSL for writing a simulated Postgres server. We're using an explicit Yoneda encoding
  // here so we can define a functor instance below, which is necessary if we want to run this
  // program step by step (which we do).
  sealed trait Step[+A]
  case class Respond[A](m: BackendMessage, k: Unit => A) extends Step[A]
  case class Expect[A](h: PartialFunction[FrontendMessage, A]) extends Step[A]
  object Step {
    implicit val FunctorStep: Functor[Step] =
      new Functor[Step] {
        def map[A,B](fa: Step[A])(f: A => B): Step[B] =
          fa match {
            case Respond(ms, k) => Respond(ms, k andThen f)
            case Expect(h)      => Expect(h andThen f)
          }
      }
  }

  // To ensure that simulations all terminate cleanly, we will provide a value that must be
  // returned but has no public constructor, so you can only construct it with `halt` below.
  sealed trait Halt
  private object Halt extends Halt

  // Smart constructors for our DSL.
  def respond(m: BackendMessage): Free[Step, Unit] = Free.liftF(Respond(m, identity))
  def expect[A](h: PartialFunction[FrontendMessage, A]): Free[Step, A] = Free.liftF(Expect(h))
  def flatExpect[A](h: PartialFunction[FrontendMessage, Free[Step, A]]): Free[Step, A] = expect(h).flatten
  def halt: Free[Step, Halt] = expect { case _ => Halt }

  // Our server runtime consists of a queue of outgoing messages, plus a continuation that consumes
  // an incoming message and computes the next continuation.
  case class MockState(queue: Queue[BackendMessage], expect: Expect[Free[Step, _]])

  // To advance the machine we run it until we reach an Expect node. If we reach a Respond then we
  // enqueue its message and continue to the next step. If we reach a terminal node then the
  // simulation has ended and we can't process any more messages.
  def advance(m: Free[Step, _], q: Queue[BackendMessage]): Either[String, MockState] =
    m.resume match {
      case Left(Respond(m, k)) => advance(k(()), q :+ m)
      case Left(Expect(h))     => MockState(q, Expect(h)).asRight
      case Right(_)            => "The simulation has ended.".asLeft
    }

  // To receive a message we dequeue from our state. Because `advance` above enqueues eagerly it is
  // impossible to miss messages. If there are no pending messages we're stuck.
  def receive(ms: MockState): Either[String, (BackendMessage, MockState)] =
    ms.queue.dequeueOption match {
      case Some((m, q)) => (m, ms.copy(queue = q)).asRight
      case None         => "No pending messages.".asLeft
    }

  // To send a message we pass it to the continuation and compute the next one.
  def send(m: FrontendMessage, ms: MockState): Either[String, MockState] =
    advance(ms.expect.h(m), ms.queue)

  // Ok so now we have what we need to construct a MessageSocket whose soul is a simulation defined
  // with the DSL above. We'll advance the machine first, ensuring that there's at least one
  // Expect node inside, otherwise we're done early. This will never happen because users aren't
  // able to construct a value of type `Halt` so it's kind of academic. Anyway we'll stuff our
  // initial MockState into a Ref and then we consult it when we `send` and `receive`, using the
  // standard ref-state-machine pattern. This is pretty cool really.
  def apply(m: Free[Step, Halt]): IO[MessageSocket[IO]] =
    advance(m, Queue.empty).leftMap(new IllegalStateException(_)).liftTo[IO].flatMap(Ref[IO].of).map { ref =>
      new MessageSocket[IO] {

        def receive: IO[BackendMessage] =
          ref.modify { ms =>
            SimulatedMessageSocket.receive(ms) match {
              case Right((m, ms)) => (ms, m.pure[IO])
              case Left(err)      => (ms, IO.raiseError(new IllegalStateException(err)))
            }
          } .flatten

        def send(message: FrontendMessage): IO[Unit] =
          ref.modify { ms =>
            SimulatedMessageSocket.send(message, ms) match {
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