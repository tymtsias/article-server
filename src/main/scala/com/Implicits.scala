package com

import cats.effect.IO
import com.models.{Article, ArticleModel, CreateArticleModel, CreateArticleRequest, CreatingArticleResponse, TagsResponse}
import com.models.auth._
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.semiauto._
import Author.encoder

import java.sql.Timestamp
import scala.concurrent.{ExecutionContext, Future}

object Implicits {
  implicit val loginUserDecoder: Decoder[LoginUser] = deriveDecoder
  implicit val newUserDecoder: Decoder[NewUser] = deriveDecoder
  implicit val userResponseEncoder: Encoder[UserResponse] = deriveEncoder
  implicit val loginUserModelDecoder: Decoder[LoginUserModel] = deriveDecoder
  implicit val newUserModelDecoder: Decoder[NewUserModel] = deriveDecoder
  implicit val createArticleModelDecoder: Decoder[CreateArticleModel] = deriveDecoder
  implicit val createArticleRequestDecoder: Decoder[CreateArticleRequest] =deriveDecoder


  implicit val timestampEncoder: Encoder[Timestamp] = (a: Timestamp) => a.toLocalDateTime.asJson
  implicit val articleEncoder: Encoder[Article] = deriveEncoder
  implicit val articleModelEncoder: Encoder[ArticleModel] = deriveEncoder
  implicit val tagsResponseEncoder: Encoder[TagsResponse] = deriveEncoder
  implicit val creatingArticleResponseEncoder: Encoder[CreatingArticleResponse] = deriveEncoder

  implicit val userResponseModelEncoder: Encoder[UserResponseModel] = deriveEncoder
  implicit class FutureOps[T](v: Future[T]) {
    val toIO = IO.fromFuture(IO(v))
  }



}