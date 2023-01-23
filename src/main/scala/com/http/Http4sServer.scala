package com.http

import cats.effect._
import cats.effect.unsafe.implicits.global
import com.Conf
import com.db.{ArticlesRepo, TagsRepo, UserRepo}
import com.models.auth.{LoginUser, LoginUserModel, NewUser, NewUserModel, UserResponse}
import com.Implicits.FutureOps
import com.models.{ArticleModel, TagsResponse}
import org.http4s._
import org.http4s.dsl.io._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.server.middleware._

import scala.concurrent.Future

object TagQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("tag")

object AuthorQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("author")

object FavoriedQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Boolean]("favorited")

object OffsetQueryParamMatcher extends QueryParamDecoderMatcher[Int]("offset")

object LimitQueryParamMatcher extends QueryParamDecoderMatcher[Int]("limit")

class Http4sServer(userRepo: UserRepo, articleRepo: ArticlesRepo, tagsRepo: TagsRepo) {
  def app =
    HttpRoutes
      .of[IO] {
        case request @ POST -> Root / "users" / "login" =>
          request
            .as[LoginUserModel]
            .flatMap { loginUser =>
              userRepo
                .verify(loginUser.user)
                .toIO
                .flatMap {
                  case Some(userInfo) =>
                    Ok(
                      UserResponse
                        .build(loginUser.user, userInfo)
                        .asJson
                        .noSpaces)
                  case None =>
                    IO(Response(status = Unauthorized.status))
                }
            }

        case request @ POST -> Root / "users" =>
          request
            .as[NewUserModel]
            .flatMap { newUser =>
              userRepo
                .save(newUser.user)
                .toIO
                .flatMap(
                  _ =>
                    Ok(
                      UserResponse
                        .build(newUser.user)
                        .asJson
                        .noSpaces))
            }

        case GET -> Root / "user" =>
          Ok("""{
        |  "user": {
        |    "email": "string",
        |    "token": "string",
        |    "username": "string",
        |    "bio": "string",
        |    "image": "string"
        |  }
        |}""".stripMargin)

        case request @ PUT -> Root / "users" =>
          ???
        case GET -> Root / "articles" :? TagQueryParamMatcher(tag) +& AuthorQueryParamMatcher(author) +& FavoriedQueryParamMatcher(
              favorited) +& OffsetQueryParamMatcher(offset) +& LimitQueryParamMatcher(limit) =>
          articleRepo
            .get(tag, author, favorited, offset, limit)
            .toIO
            .flatMap { articlesList =>
              Ok(ArticleModel(articlesList, articlesList.size).asJson.noSpaces)
            }

        case GET -> Root / "tags" =>
          tagsRepo
            .getAll()
            .toIO
            .flatMap(tags => Ok(TagsResponse(tags.toSeq).asJson.noSpaces))
      }

  val corsConfig =
    CORSConfig.default
      .withAllowCredentials(true)
      .withAllowedOrigins(_ => true)

  def run() = {
    BlazeServerBuilder[IO]
      .bindHttp(Conf.httpPort, Conf.httpHost)
      .withHttpApp(CORS(app, corsConfig).orNotFound)
      .resource
      .useForever
      .as {
        ExitCode.Success
      }
  }
}
