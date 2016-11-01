package services

import com.google.api.client.http.{HttpRequest, HttpRequestInitializer}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.{YouTube, YouTubeRequestInitializer}
import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.{JsObject, JsValue, Reads}
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Failure

@Singleton
class Movie @Inject()(cache: CacheApi, ws: WSClient, conf: Configuration)(implicit ec: ExecutionContext) {
	val tmdbKey: String = conf.getString("tmdb.key").get
	val youtubeKey: String = conf.getString("youtube.key").get

	implicit class SafeFuture[A](private val f: Future[A]) {
		def safe: Future[Option[A]] = f.map(Option.apply).recover { case _ => None }
	}

	def cached[A](key: String)(action: => Future[A]) : Future[A] = cache.getOrElse(key, 24.hours) {
		action.andThen{
			case Failure(e) => cache.remove(key)
		}
	}

	def req[T](path: String)(action: WSResponse => T) = {
		ws.url(s"https://api.themoviedb.org/3$path${if (path.contains("?")) "&" else "?"}api_key=$tmdbKey").get.map(action)
	}

	def findTMDBId(name: String) : Future[Int] = cached(s"id/$name") {
		req(s"/search/movie?query=$name") { r => ((r.json \ "results")(0) \ "id").as[Int] }
	}

	def getInfo(id: Int) : Future[JsValue] = cached(s"info/$id") {
		req(s"/movie/$id") { r  => r.json }
	}

	def getInfo(title: String) : Future[JsValue] = findTMDBId(title).flatMap(getInfo)

	def getInfo[A:Reads](title: String, field: String) : Future[A] = (getInfo(title).map(f => (f \ field).as[A]))

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

}
