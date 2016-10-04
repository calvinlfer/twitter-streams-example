package controllers

import javax.inject._

import akka.actor.ActorSystem
import play.api._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee._
import play.extras.iteratees._
import play.api.libs.json.JsObject
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.Future

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class HomeController @Inject()(config: Configuration, wsClient: WSClient, system: ActorSystem) extends Controller {

  /**
    * Create an Action to render an HTML page with a welcome message.
    * The configuration in the `routes` file means that this method
    * will be called when the application receives a `GET` request with
    * a path of `/`.
    *
    * We mark the request as implicit so it becomes an implicit value and Play can use it
    * when rendering its Twirl template views
    */
  def index = Action { implicit request =>
    Ok(views.html.index("Your new application is ready."))
  }

  val loggingIteratee = Iteratee.foreach[JsObject] {
    json => Logger.info(json.toString)
  }

  // Set up a joined iteratee (Sink) and enumerator (Source) => this is essentially an Enumeratee (Flow)
  //                                    Flow/Enumeratee
  //                         __________________________________________
  //                        |                                          |
  // Source/Enumerator ---> | Sink/Iteratee          Source/Enumerator | ---> Sink/Iteratee
  //                        |__________________________________________|
  val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]

  // tack on Iteratees/Flows to the Enumerator/Source (remember that doing this is still a Enumerator/Source)
  // &> signifies attaching an Enumeratee/Flow to an existing Stream object
  // Encoding.decode() is a predefined flow along with Enumeratee.grouped which repeats an Iteratee multiple times
  // to convert it to a Flow (in Play, Iteratees (similar to Akka Streams Sinks) can produces side effect values)
  val jsonStream: Enumerator[JsObject] = enumerator &> Encoding.decode() &> Enumeratee.grouped(JsonIteratees.jsSimpleObject)

  // Hooking an Enumerator (source) to an Iteratee (sink)
  // How are values coming in you ask? Who is driving the Enumerator?
  // Remember the Concurrent.joined[Array[Byte]] which allows us to connect
  // an Iteratee (Sink) producing some side effect values to an Enumerator (Source)
  jsonStream run loggingIteratee

  // use Action.async so we may return a Future result
  def tweets = Action.async {
    val credentials = obtainCredentialsFromHOCON
    credentials map {
      case (consumerKey, requestToken) =>
        wsClient.url("https://stream.twitter.com/1.1/statuses/filter.json")
          .sign(OAuthCalculator(consumerKey, requestToken))
          .withQueryString("track" -> "cat")
          .get {
            response =>
              Logger.info(s"Status: ${response.status}")
              // Play expects we consume the streaming response by providing an Iteratee to consume it
              iteratee
          }
          .map { _ =>
            Ok("Stream closed")
          }

    } getOrElse {
      Future.successful(InternalServerError("Twitter credentials are missing"))
    }
  }

  // Note: we use a for-comprehension because each getString returns an Option[String]
  // We want to ensure all Options are present in order to build out ConsumerKey and RequestToken
  private def obtainCredentialsFromHOCON: Option[(ConsumerKey, RequestToken)] = for {
    apiKey <- config.getString("twitter.apiKey")
    apiSecret <- config.getString("twitter.apiSecret")
    token <- config.getString("twitter.token")
    tokenSecret <- config.getString("twitter.tokenSecret")
  } yield (ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))
}
