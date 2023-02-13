package com.utils
import com.http.requests.{ChangeUserRequest, CreateArticleRequest, LoginUserRequest, NewUserRequest}
import com.models.auth._
import com.models.CreateArticleModel
import io.circe.Decoder
import io.circe.generic.semiauto._

object Decoders {
  implicit val loginUserDecoder: Decoder[LoginUser] = deriveDecoder
  implicit val newUserDecoder: Decoder[NewUser] = deriveDecoder
  implicit val loginUserModelDecoder: Decoder[LoginUserRequest] = deriveDecoder
  implicit val newUserModelDecoder: Decoder[NewUserRequest] = deriveDecoder
  implicit val createArticleModelDecoder: Decoder[CreateArticleModel] = deriveDecoder
  implicit val createArticleRequestDecoder: Decoder[CreateArticleRequest] = deriveDecoder
  implicit val changeUserRequestDecoder: Decoder[ChangeUserRequest] = deriveDecoder
  implicit val fullUserDecoder: Decoder[FullUser] = deriveDecoder
}
