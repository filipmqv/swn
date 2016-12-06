import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source
import scala.language.postfixOps

case class Ping(value: Int)
case class Pong(value: Int)

class ServerActor(serverId: Int) extends Actor {
  def receive = {
    case Ping(value) => {
      println(value)
      sender ! "ok"
    }
  }
}

object Server extends App {
  implicit val timeout = Timeout(500 seconds)
  args.foreach(println)
  val system = ActorSystem("RemoteActorSystem", ConfigFactory.load("server"))
  val remoteActor = system.actorOf(Props(new ServerActor(1)), "RemoteActor")

  val future = remoteActor ? Ping(6)
  println("Ask sent")
  val result = Await.result(future, Duration.Inf).asInstanceOf[String]
  println(result)
  system.terminate()
}