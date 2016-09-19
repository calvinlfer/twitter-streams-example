package controllers

import javax.inject._

import akka.actor.ActorSystem
import play.api._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee._
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
    */
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  val loggingIteratee = Iteratee.foreach[Array[Byte]] {
    array => Logger.info(array.map(_.toChar).mkString)
  }

  // use Action.async so we may return a Future result
  def tweets = Action.async {
    val credentials = obtainCredentialsFromHOCON
    credentials map {
      case (consumerKey, requestToken) =>
        wsClient.url("https://stream.twitter.com/1.1/statuses/filter.json")
          .sign(OAuthCalculator(consumerKey, requestToken))
          .withQueryString("track" -> "reactive")
          .get {
            response =>
              Logger.info(s"Status: ${response.status}")
              loggingIteratee
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
