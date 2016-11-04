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
import services.MovieService

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.{higherKinds, implicitConversions}
import scala.util.Try

class Application @Inject()(ws: WSClient, movieService: MovieService)(implicit ec: ExecutionContext) extends Controller {
	implicit def textExtractor(a: HtmlExtractor[String]): HtmlExtractor[JsValueWrapper] = a.map(JsString.apply)

	implicit def FutureJsValue[T](f: Future[T])(implicit w: Writes[T]): JsValueWrapper = {
		Try(w.writes(Await.result(f, 5.seconds))).getOrElse[JsValue](JsNull)
	}

	def index(path: String) = Action {
		val browser = JsoupBrowser()
		val doc = browser.get(s"http://google.com/movies?near=$path")
		val element = doc.body.select("div.movie_results")
		if (element.isEmpty) Ok(JsArray(Nil)) else {
			val cinemas = element.head.select("div.theater").map(c => {
				val movie = movieService.getMovie(c >> text("h2"))
				Json.obj(
					"name" -> (c >> text("h2")),
					"address" -> (c >> text(".desc .info")),
					"movies" -> (c >> elementList(".movie")).map(f => {
						val name = f >> text(".name")
						Json.obj(
							"name" -> name,
							"poster" -> movie.poster,
							"trailer" -> movie.trailer,
							"imdb" -> movie.getIMDBUrl(name),
							"duration" -> movie.getDuration(name),
							"genres" -> movie.getGenres(name),
							"times" -> (f >> elementList(".times span")).map(t => t.text.replaceAll("[^0-9:]", "")).filter(_.nonEmpty)
						)
					})
				)
			})
			Ok(JsArray(cinemas.toSeq))

		}

	}


}
