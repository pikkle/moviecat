package services

import com.google.inject.{Inject, Singleton}
import models.Movie
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.{JsObject, Reads}
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure}

@Singleton
class MovieService @Inject()(cache: CacheApi, conf: Configuration)(implicit ec: ExecutionContext, ws: WSClient) {
	private implicit val self = this
	val tmdbKey: String = conf.getString("tmdb.key").get
	val youtubeKey: String = conf.getString("youtube.key").get

	def getMovie(title: String): Future[Movie] = cached(s"movies/$title") {
		findTMDBId(title).flatMap { id =>
			for {
				title <- getTitle(id)
				poster <- getPoster(id)
				trailer <- getTrailer(id)
			} yield Movie(id, title, poster, trailer)
		}
	}

	def getInfo(id: Int): Future[JsObject] = callTMDB(s"/movie/$id") { r => r.json.as[JsObject] }

	def getVideos(id: Int): Future[JsObject] = callTMDB(s"/movie/$id/videos") { r => (r.json \ "results")(0).as[JsObject] }

	private def findTMDBId(title: String): Future[Int] = cached(s"ids/$title", Duration.Inf){
		callTMDB(s"/search/movie?query=$title") {
			r => ((r.json \ "results") (0) \ "id").as[Int]
		}
	}

	private def callTMDB[T](path: String)(action: WSResponse => T): Future[T] = {
		val op = if (path.contains("?")) "&" else "?"
		ws.url(s"https://api.themoviedb.org/3$path${op}api_key=$tmdbKey").get.map(action)
	}

	def cached[A](key: String, time: Duration = 24.hours)(action: => Future[A]): Future[A] = cache.getOrElse(key, time) {
		action.andThen {
			case Failure(e) => cache.remove(key)
		}
	}

	def getTitle(id: Int) = getInfo(id).map(getField[String]("title"))
	def getPoster(id: Int) = getInfo(id).map(getField[String]("poster_path")).map("https://image.tmdb.org/t/p/w500" + _)
	def getTrailer(id: Int) = getVideos(id).map(getField[String]("key")).map("" + _)

	private def getField[T: Reads](field: String)(obj: JsObject): T = (obj \ field).as[T]

}
