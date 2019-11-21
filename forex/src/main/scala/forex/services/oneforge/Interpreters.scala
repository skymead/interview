package forex.services.oneforge

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import cats.NotNull
import forex.domain._
import io.circe.Json
import io.circe.generic.auto._
import io.circe.parser._
import monix.eval.Task
import org.atnos.eff._
import org.atnos.eff.addon.monix.task._
import org.joda.time.DateTime

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object Interpreters {
  def dummy[R](
      implicit
      m1: _task[R]
  ): Algebra[Eff[R, ?]] = new Dummy[R]
}

final class Dummy[R] private[oneforge] (implicit m1: _task[R]) extends Algebra[Eff[R, ?]] {
  implicit lazy val system: ActorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  case class ForgeResponse(symbol: String, price: Double, bid: Double, ask: Double, timestamp: Long)

  var cache: Map[String, ForgeResponse] = Map().empty

  override def get(pair: Rate.Pair): Eff[R, Error Either Rate] =
    for {
      result ← fromTask(Task.now(Rate(pair, Price(BigDecimal(price(pair))), Timestamp.now)))
    } yield Right(result)

  def price(pair: Rate.Pair): Double = {
    if (cache.get(s"${pair.from}${pair.to}").isDefined && lessThan5MinutesPassed(cache(s"${pair.from}${pair.to}").timestamp))
      return cache(s"${pair.from}${pair.to}").price
    val futurePrice = for {
      response ← Http().singleRequest(
        HttpRequest(
          uri = s"https://api.1forge.com/quotes?pairs=${pair.from}${pair.to}&api_key=ls1Lvc9MfuHUqbNlLdjAyJ7eGnE7CIch"
        )
      )
      entity ← Unmarshal(response.entity).to[String]
      price ← decode[List[ForgeResponse]](entity) match {
        case Left(failure) ⇒
          Future {
            0.0d
          }
        case Right(forgeResponses) ⇒
          Future {
            if (forgeResponses.nonEmpty) {
              cache = cache + (s"${pair.from}${pair.to}" → forgeResponses.head)
              forgeResponses.head.price
            } else 0.0d
          }
      }
    } yield price
    Await.result(futurePrice, 5 seconds)
  }

  def lessThan5MinutesPassed(cacheTime: Long): Boolean = {
    val now = new DateTime()
    val cachedTime = new DateTime(cacheTime*1000).plusMinutes(5)
    cachedTime.isAfter(now.toInstant)
  }
}
