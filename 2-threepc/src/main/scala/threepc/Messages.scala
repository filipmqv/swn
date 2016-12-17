package threepc

import akka.actor.ActorRef

final case class StartupCoordinator(cohorts: Map[ActorRef, Int])
final case class StartupCohort(id: Int)

// Coordinator sends:
final case class StartCommit(commitId: Int)
final case class CommitRequest(commitId: Int) //CanCommit?
final case class Prepare(commitId: Int)
final case class Commit(commitId: Int)

// Cohort sends:
final case class Agree(commitId: Int)
final case class Abort(commitId: Int)
final case class Ack(commitId: Int)

final case class CommitFinished(commitId: Int)

final case class Print(actor: List[ActorRef], action: Symbol, messageType: Symbol, commitId: Int, stateType: Symbol)

final case class LoseMessage(which: Symbol)
final case class Nodes(nodes: Map[ActorRef, Int])
final case class Text(text: String)