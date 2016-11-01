package controllers

import com.google.inject.Inject
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import play.api._
import play.api.libs.ws.WSClient
import play.api.mvc._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import net.ruippeixotog.scalascraper.scraper.HtmlExtractor
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import services.Movie

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.{higherKinds, implicitConversions}
import scala.util.Try

class Application @Inject()(ws: WSClient, movie: Movie)(implicit ec: ExecutionContext) extends Controller {
	implicit def textExtractor(a: HtmlExtractor[String]): HtmlExtractor[JsValueWrapper] = a.map(JsString.apply)

	implicit def FutureJsValue[T](f: Future[T])(implicit w: Writes[T]): JsValueWrapper = {
		Try(w.writes(Await.result(f, 5.seconds))).getOrElse[JsValue](JsNull)
	}

	def index(path: String) = Action {
		val browser = JsoupBrowser()
		val doc = browser.get("http://google.com/movies?near=Lausanne")
		val element = doc.body.select("div.movie_results").head

		val cinemas = element.select("div.theater").map(c => {
			Json.obj(
				"name" -> (c >> text("h2")),
				"address" -> (c >> text(".desc .info")),
				"movies" -> (c >> elementList(".movie")).map(f => {
					val name = f >> text(".name")
					Json.obj(
						"name" -> name,
						"poster" -> movie.getPoster(name),
						"trailer" -> movie.getTrailer(name),
						//"imdb" -> movie.getIMDBUrl(name),
						//"genres" -> movie.getGenres(name),
						"info" -> (f >> text(".info")),
						"times" -> (f >> elementList(".times span")).map(t => t.text.replaceAll("[^0-9:]", "")).filter(_.nonEmpty)
					)
				})
			)
		})



		Ok(JsArray(cinemas.toSeq))
	}


}
