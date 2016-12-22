package threepc

import akka.actor.ActorRef

final case class StartupCoordinator(cohorts: Map[ActorRef, Int])
final case class StartupCohort(id: Int)

sealed abstract class Msg()
// Coordinator sends:
final case class StartCommit(commitId: Int) extends Msg
final case class CommitRequest(commitId: Int) extends Msg //CanCommit?
final case class Prepare(commitId: Int) extends Msg
final case class Commit(commitId: Int) extends Msg
// Abort (same message as cohort sends)

// Cohort sends:
final case class Agree(commitId: Int) extends Msg
final case class Abort(commitId: Int) extends Msg
final case class Ack(commitId: Int) extends Msg

final case class CommitFinished(commitId: Int) extends Msg

final case class Print(actor: List[ActorRef], action: Symbol, messageType: Symbol, commitId: Int, stateType: Symbol)

final case class Nodes(coordinator: ActorRef, cohorts: Map[ActorRef, Int])
final case class DoFail()
final case class AbortNextTime()
final case class TimeIsOut(commitId: Int)
final case class Text(text: String)