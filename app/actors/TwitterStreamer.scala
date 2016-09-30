package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import play.api.libs.json.Json

class TwitterStreamer(out: ActorRef) extends Actor with ActorLogging {
  // handles receiving of new messages
  override def receive: Receive = {
    case "subscribe" =>
      log.info("Received a subscription from a client")
      // sends a JSON object to the out Actor
      out ! Json.obj("text" -> "Hello World")
  }
}

object TwitterStreamer {
  // An Actor is created from its Props
  def props(out: ActorRef) = Props(new TwitterStreamer(out))
}