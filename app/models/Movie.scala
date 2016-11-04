package models

import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.ws.WSClient
import services.MovieService

import scala.concurrent.Future

case class Movie(id: Int)(implicit ws: WSClient, movieService: MovieService) {
	val infos = movieService.getInfo(id).map(_.as[JsObject])
	val videos = movieService.getVideos(id)

	val title = getField[String](infos, "")
	val poster = getField[String](infos, "poster_path").map("https://image.tmdb.org/t/p/w500" + _)
	val trailer = getField[String](videos, "poster_path").map("https://image.tmdb.org/t/p/w500" + _)


	private def getField[T](obj: JsObject, field: String): Future[T] = infos.map(j => (j \ field).as[T])
}
