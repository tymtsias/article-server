package com.utils

import com.http.responses.{ArticlesResponse, CreatingArticleBody, CreatingArticleResponse, TagsResponse, UserResponse}
import com.models.auth._
import com.models.Article
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._

import java.sql.Timestamp


object Encoders {
  implicit val userResponseEncoder: Encoder[UserData] = deriveEncoder


  implicit val timestampEncoder: Encoder[Timestamp] = (a: Timestamp) => a.toLocalDateTime.asJson
  implicit val articleEncoder: Encoder[Article] = deriveEncoder
  implicit val articleModelEncoder: Encoder[ArticlesResponse] = deriveEncoder
  implicit val tagsResponseEncoder: Encoder[TagsResponse] = deriveEncoder
  implicit val creatingArticleResponseEncoder: Encoder[CreatingArticleResponse] = deriveEncoder
  implicit val creatingArticleBodyEncoder: Encoder[CreatingArticleBody] = deriveEncoder
  implicit val userResponseModelEncoder: Encoder[UserResponse] = deriveEncoder

}
