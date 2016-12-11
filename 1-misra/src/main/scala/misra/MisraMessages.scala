package misra

import akka.actor.ActorRef

final case class Ping(value: Int)
final case class Pong(value: Int)
final case class Startup(id: Int, nextNode: ActorRef) // address of actor next in the ring
final case class Print(text: String)
