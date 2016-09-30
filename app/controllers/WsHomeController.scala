package controllers

import javax.inject._

import actors.TwitterStreamer
import akka.actor.{ActorRef, ActorSystem}
import play.api._
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.Play.current    // Play needs this

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class WsHomeController @Inject()(config: Configuration, wsClient: WSClient, system: ActorSystem) extends Controller {
  def streamingTweets: WebSocket[String, JsValue] = WebSocket.acceptWithActor[String, JsValue] {
    (request: RequestHeader) =>
      // out actorRef represents connection to the WS client
      // note that any message the clients sends will be sent directly to the Twitter actor
      // the Actor uses the out ActorRef to communicate with the WS client
      (out: ActorRef) =>
        TwitterStreamer.props(out)
  }
}
