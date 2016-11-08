package controllers

import com.google.inject.Inject
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import play.api.libs.ws.WSClient
import play.api.mvc._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.scraper.HtmlExtractor
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import services.MovieService

import scala.concurrent.{ExecutionContext, Future}
import scala.language.{higherKinds, implicitConversions}

class Application @Inject()(ws: WSClient, movieService: MovieService)(implicit ec: ExecutionContext) extends Controller {
	implicit def textExtractor(a: HtmlExtractor[String]): HtmlExtractor[JsValueWrapper] = a.map(JsString.apply)


	def index(path: String) = Action.async {
		val browser = JsoupBrowser()
		val doc = browser.get(s"http://google.com/movies?near=$path")
		val element = doc.body.select("div.movie_results")
		println(element)
		if (element.isEmpty) Future.successful(Ok(JsArray(Nil)))
		else {
			Future.sequence(element.head.select("div.theater").map(c => {
				Future.sequence((c >> elementList(".movie")).map(_ >> text(".name")).map(movieService.getMovie)).map { movies =>
					Json.obj(
						"name" -> (c >> text("h2")),
						"address" -> (c >> text(".desc .info")),
						"movies" -> movies
					)
				}
			})).map(cinemas => Ok(JsArray(cinemas.toSeq)))

		}
	}

}
