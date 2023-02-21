package com.models.auth

import io.circe.{ Encoder, Json }
import io.circe.generic.auto._
import io.circe.syntax._

case class Author(userInfo: UserInfo, following: Boolean)

object Author {
  implicit val encoder: Encoder[Author] = (a: Author) =>
    a.userInfo.asJson.deepMerge(Json.obj("following" -> a.following.asJson))
  def fromUserResponse(userResponse: UserData, following: Boolean) =
    Author(UserInfo(userResponse.bio, userResponse.username, userResponse.image), following)
}
