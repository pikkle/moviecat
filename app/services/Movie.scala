package services

import com.google.api.client.http.{HttpRequest, HttpRequestInitializer}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.{YouTube, YouTubeRequestInitializer}
import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.JsValue
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

	def cached[A](key: String)(action: => Future[A]) : Future[A] = cache.getOrElse(key, 5.minutes) {
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
		findTMDBId(name).flatMap(getInfo).map(info => "https://image.tmdb.org/t/p/w500" + (info \ "poster_path").as[String]).safe
	}

	/*
	def getGenres(name: String) : Future[Option[String]] = cached(s"genres/$name") {
		findTMDBId(name).flatMap(getInfo).map(info => (info \ "genres").as[String]).safe
	}
	*/

	def getIMDBUrl(name: String) : Future[Option[String]] = cached(s"imdbid/$name") {
		findTMDBId(name).flatMap(getInfo).map(info => "http://www.imdb.com/title/" + (info \ "imdb_id").as[String]).safe
	}

}
