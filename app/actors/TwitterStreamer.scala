package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import play.api.{Logger, Play}
import play.api.libs.iteratee.{Concurrent, Enumeratee, Enumerator, Iteratee}
import play.api.libs.json.{JsObject, Json}
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
import play.api.libs.ws.WS
import play.extras.iteratees.{Encoding, JsonIteratees}
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global

class TwitterStreamer(out: ActorRef) extends Actor with ActorLogging {
  import TwitterStreamer._
  // handles receiving of new messages
  override def receive: Receive = {
    case "subscribe" =>
      log.info("Received a subscription from a client")
      // send the client the Twitter Stream :-)
      subscribe(out)
  }
}

object TwitterStreamer {
  // An Actor is created from its Props
  def props(out: ActorRef) = Props(new TwitterStreamer(out))

  private var optBroadcastEnumerator: Option[Enumerator[JsObject]] = None

  private def obtainCredentialsFromHOCON: Option[(ConsumerKey, RequestToken)] = for {
    apiKey <- Play.configuration.getString("twitter.apiKey")
    apiSecret <- Play.configuration.getString("twitter.apiSecret")
    token <- Play.configuration.getString("twitter.token")
    tokenSecret <- Play.configuration.getString("twitter.tokenSecret")
  } yield (ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))

  def connect(): Unit = {
    val credentials = obtainCredentialsFromHOCON
    credentials map {
      case (consumerKey, requestToken) =>
        // Set up a joined iteratee (Sink) and enumerator (Source) => this is essentially an Enumeratee (Flow)
        //                                    Flow/Enumeratee
        //                         __________________________________________
        //                        |                                          |
        // Source/Enumerator ---> | Sink/Iteratee          Source/Enumerator | ---> Sink/Iteratee
        //                        |__________________________________________|
        val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]

        // the Enumerator will be emitting Array[Byte] which we convert into JSON
        // the Enumerator Source is actually being fed by the iteratee (consuming from Twitter Streams)
        val jsonStream: Enumerator[JsObject] =
          enumerator &> Encoding.decode() &> Enumeratee.grouped(JsonIteratees.jsSimpleObject)

        // Note: be is an Enumerator/Source
        val (be, _) = Concurrent.broadcast(jsonStream)
        optBroadcastEnumerator = Some(be)

        val url = "https://stream.twitter.com/1.1/statuses/filter.json"
        WS.url(url)
          .sign(OAuthCalculator(consumerKey, requestToken))
          .withQueryString("track" -> "cats")
          .get { response =>
            Logger.info("Status: " + response.status)
            // the iteratee will consume the Streaming Twitter JSON (as Array[Byte]) and feed it into the Enumerator
            // that it is hooked to via Concurrent.joined...
            iteratee
          }
          .map { _ => Logger.info("Twitter stream closed") }
    } getOrElse {
      Logger.error("Twitter credentials missing")
    }
  }

  def subscribe(recipient: ActorRef): Unit = {
    if (optBroadcastEnumerator.isEmpty) connect()
    // for each message received at the Iteratee (Sink), we send it to the recipient actor representing the WS client
    val twitterClient = Iteratee.foreach[JsObject] {jsObj => recipient ! jsObj}
    // connect the Enumerator to the Iteratee to create a Runnable Flow
    optBroadcastEnumerator.foreach(bE => bE run twitterClient)
  }
}