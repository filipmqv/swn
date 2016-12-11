package misra

import akka.actor.ActorSelection

//#messages
//final case class StatsJob(text: String)
//final case class StatsResult(meanWordLength: Double)
//final case class JobFailed(reason: String)
final case class Ping(value: Int)
final case class Pong(value: Int)
final case class NextNode(address: ActorSelection) // address of actor next in the ring
//#messages
