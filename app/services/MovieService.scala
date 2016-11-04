package services

import com.google.api.client.http.{HttpRequest, HttpRequestInitializer}
import com.google.api.services.youtube.{YouTube, YouTubeRequestInitializer}
import com.google.inject.{Inject, Singleton}
import models.Movie
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.{JsObject, JsValue, Reads}
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class MovieService @Inject()(cache: CacheApi, conf: Configuration)(implicit ec: ExecutionContext, ws: WSClient) {
	val tmdbKey: String = conf.getString("tmdb.key").get
	val youtubeKey: String = conf.getString("youtube.key").get

	def getMovie(title: String): Option[Movie] = cached(s"movies/$title") {
		Movie(findTMDBId(title))
	}

	def getInfo(id: Int): Future[JsValue] = callTMDB(s"/movie/$id") { r => r.json }

	def getVideos(id: Int): Future[JsValue] = callTMDB(s"/movie/$id/videos") { r => r.json }

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

	/*

	implicit class SafeFuture[A](private val f: Future[A]) {
		def safe: Future[Option[A]] = f.map(Option.apply).recover { case _ => None }
	}


	def getInfo(name: String) : Future[JsValue] = findTMDBId(name).flatMap(getInfo)

	def getTrailer2(name: String) : Future[Option[String]] = cached(s"trailers/$name") {
		findTMDBId(name).flatMap(id => Future {
			""
		}.safe)
	}

	def getTrailer(name: String) : Future[Option[String]] = cached(s"trailer/$name") {
		Future {
			val transport = new NetHttpTransport()
			val factory = new JacksonFactory()
			val httpRequestInit = new HttpRequestInitializer {
				override def initialize(re: HttpRequest ) =  { }
			}
			val service = new YouTube.Builder(transport, factory, httpRequestInit).setApplicationName("test").setYouTubeRequestInitializer(new YouTubeRequestInitializer(youtubeKey)).build()
			val video = service.search().list("snippet").setQ(s"$name - Trailer").execute().getItems.get(0)
			s"https://www.youtube.com/watch?v=${video.getId.getVideoId}"
		}.safe
	}



	def getPoster(name: String) : Future[Option[String]] = {
		getInfo[String](name, "poster_path").map("https://image.tmdb.org/t/p/w500" + _).safe
	}

	def getIMDBUrl(name: String) : Future[Option[String]] = cached(s"imdbid/$name") {
		getInfo[String](name, "imdb_id").map("http://www.imdb.com/title/" + _).safe
	}

	def getDuration(name: String) : Future[Option[Int]] = cached(s"runtime/$name") {
		getInfo[Int](name, "runtime").safe
	}

	def getGenres(name: String) : Future[Option[String]] = {
		getInfo[Seq[JsObject]](name, "genres").map(_.map(o => (o \ "name").as[String]).mkString(", ")).safe
	}
*/
}
