package threepc

import akka.actor.ActorRef

final case class StartupCoordinator(cohorts: Map[ActorRef, Int])
final case class StartupCohort(id: Int)


final case class Print(actor: ActorRef, what: Symbol, text: Int)

final case class LoseMessage(which: Symbol)
final case class Nodes(nodes: Map[ActorRef, Int])
final case class Text(text: String)