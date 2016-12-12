package misra

import akka.actor.ActorRef

final case class Ping(value: Int)
final case class Pong(value: Int)
final case class Startup(id: Int, nextNode: ActorRef) // address of actor next in the ring
final case class Print(actor: ActorRef, what: Symbol, text: Int)
final case class Job(value: Int)
final case class JobDone(value: Int)
final case class Wait(value: Int)
final case class WaitDone(value: Int)
