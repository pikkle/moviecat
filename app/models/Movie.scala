package models

import play.api.libs.json.{JsValue, Json, Writes}

case class Movie(id: Int, title: String, trailer: String, poster: String) {}

object Movie {
	implicit val movieWrites: Writes[Movie] = new Writes[Movie] {
		override def writes(movie: Movie): JsValue = Json.obj(
			"title" -> movie.title,
			"poster" -> movie.poster,
			"trailer" -> movie.trailer
		)
	}
}
