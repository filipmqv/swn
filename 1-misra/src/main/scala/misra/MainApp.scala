package misra

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object MainApp {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      startup(Seq("0"))
    } else {
      startup(Seq("2551", "2552")) // 2 seed-nodes
      Client.main(args)
    }
  }

  def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>
      // Override the configuration of the port when specified as program argument
      val config =
        ConfigFactory.parseString(s"akka.remote.netty.tcp.port=" + port).withFallback(
          ConfigFactory.parseString("akka.cluster.roles = [compute]")).
          withFallback(ConfigFactory.load("misra"))

      val system = ActorSystem("ClusterSystem", config)

      system.actorOf(Props[MisraActor], name = "misraService")
    }
  }
}

