//package controllers

//import javax.inject._
//
//import akka.actor.ActorSystem
//import akka.stream.ActorMaterializer
//import akka.stream.scaladsl.Sink
//import play.api._
//import play.api.libs.concurrent.Execution.Implicits._
//import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
//import play.api.libs.ws.{StreamedResponse, WSClient}
//import play.api.mvc._
//
//import scala.concurrent.Future

/**
  * Note: Cannot use in Play 2.4 :-(
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
//@Singleton
//class BackpressuredHomeController @Inject()(config: Configuration, wsClient: WSClient, system: ActorSystem)
//  extends Controller {
//
//  implicit val materializer = ActorMaterializer()(system)
//
//  // use Action.async so we may return a Future result
//  def tweets = Action.async {
//    val credentials = obtainCredentialsFromHOCON
//    credentials map {
//      case (consumerKey, requestToken) =>
//        wsClient.url("https://stream.twitter.com/1.1/statuses/filter.json")
//          .sign(OAuthCalculator(consumerKey, requestToken))
//          .withQueryString("track" -> "emmys")
//          .stream()
//          .flatMap {
//            case StreamedResponse(headers, bodySource) =>
//              bodySource // Akka Streams Source[ByteString, NotUsed]
//                .map { byteString => byteString.decodeString("UTF-8") }
//                .map { str => Logger.info(s"Data: $str") }
//                .runWith(Sink.ignore) // Sink ignore means we don't care about how the data gets consumed
//            // returns a Future that indicates the materialization/completion of the Stream, however, the Future
//            // does not contain anything meaningful except that it represents the completion of the Graph/Stream
//          }
//          .map(_ => Ok("Stream completed"))
//    } getOrElse {
//      Future.successful(InternalServerError("Twitter credentials are missing"))
//    }
//  }
//
//  // Note: we use a for-comprehension because each getString returns an Option[String]
//  // We want to ensure all Options are present in order to build out ConsumerKey and RequestToken
//  private def obtainCredentialsFromHOCON: Option[(ConsumerKey, RequestToken)] = for {
//    apiKey <- config.getString("twitter.apiKey")
//    apiSecret <- config.getString("twitter.apiSecret")
//    token <- config.getString("twitter.token")
//    tokenSecret <- config.getString("twitter.tokenSecret")
//  } yield (ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))
//}
