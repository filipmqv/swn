package misra

import akka.actor.{Actor, ActorSystem, Address, Props, RelativeActorPath, RootActorPath}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, MemberStatus}
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

object Client {
  def main(args: Array[String]): Unit = {
    // note that client is not a compute node, role not defined
    val system = ActorSystem("ClusterSystem")
    system.actorOf(Props(classOf[ClientActor], "/user/statsService"), "client")
  }
}

class ClientActor(servicePath: String) extends Actor {
  val cluster = Cluster(context.system)
  val servicePathElements = servicePath match {
    case RelativeActorPath(elements) => elements
    case _ => throw new IllegalArgumentException(
      "servicePath [%s] is not a valid relative actor path" format servicePath)
  }
  //import context.dispatcher
  //val tickTask = context.system.scheduler.schedule(2.seconds, 2.seconds, self, "tick")

  var nodes = Set.empty[Address]

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    //tickTask.cancel()
  }

  def receive = {
    //    case "tick" if nodes.nonEmpty =>
    //      // just pick any one
    //      val address = nodes.toIndexedSeq(ThreadLocalRandom.current.nextInt(nodes.size))
    //      println("chosen: " + address)
    //      val service = context.actorSelection(RootActorPath(address) / servicePathElements)
    //      service ! StatsJob("this is the text that will be analyzed")
    //    case result: StatsResult =>
    //      println(result)
    //    case failed: JobFailed =>
    //      println(failed)
    case Print(text) =>
      println(sender() + " " + text)
    case state: CurrentClusterState =>
      nodes = state.members.collect {
        case m if m.hasRole("compute") && m.status == MemberStatus.Up => m.address
      }
    case MemberUp(m) if m.hasRole("compute") => {
      nodes += m.address
      if (nodes.size == 5) {
        implicit val resolveTimeout = Timeout(5 seconds)
        for (i <- 0 to nodes.size) {
          val currentNodeAddr = nodes.toIndexedSeq(i%nodes.size)
          val nextNodeAddr = nodes.toIndexedSeq((i + 1)%nodes.size)

          val currentNode = Await.result(context.actorSelection(RootActorPath(currentNodeAddr) / servicePathElements)
            .resolveOne(), resolveTimeout.duration)
          val nextNode = Await.result(context.actorSelection(RootActorPath(nextNodeAddr) / servicePathElements)
            .resolveOne(), resolveTimeout.duration)

          currentNode ! Startup(i, nextNode)
        }
        val firstNode = Await.result(context.actorSelection(RootActorPath(nodes.toIndexedSeq(0)) / servicePathElements)
          .resolveOne(), resolveTimeout.duration)
        firstNode ! Ping(0)
      }

    }
    case other: MemberEvent                         => nodes -= other.member.address
    case UnreachableMember(m)                       => nodes -= m.address
    case ReachableMember(m) if m.hasRole("compute") => nodes += m.address
  }

}
