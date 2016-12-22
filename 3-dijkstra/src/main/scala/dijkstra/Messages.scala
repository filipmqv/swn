package dijkstra

import akka.actor.ActorRef

final case class Startup(id: Int, nextNode: ActorRef)

final case class State()
final case class StateAnswer(value: Int)
final case class Job(value: Int)
final case class JobDone(value: Int)
final case class Print(text: String)

//final case class Print(actor: List[ActorRef], action: Symbol, messageType: Symbol, commitId: Int, stateType: Symbol)

final case class Nodes(dijkstraActors: Map[ActorRef, Int])
final case class DoFail()
final case class Text(text: String)