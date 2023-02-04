package com.http

import cats.data.Kleisli
import cats.effect._
import com.Conf
import com.db.{ArticlesRepo, TagsRepo, UserRepo}
import com.models.auth.{LoginUser, LoginUserModel, NewUser, NewUserModel, UserInfo, UserResponse}
import com.Implicits._
import com.models.{ArticleModel, TagsResponse}
import org.http4s._
import org.http4s.dsl.io._
import io.circe.generic.semiauto._
import io.circe.syntax._
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.server.middleware._
import cats.data._
import cats.implicits.toTraverseOps
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeServerBuilder
import org.typelevel.ci.CIString
import cats.syntax.all._
import org.http4s.implicits._
import org.http4s.server.Router
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext


object TagQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("tag")

object AuthorQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("author")

object FavoriedQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Boolean]("favorited")

object OffsetQueryParamMatcher extends QueryParamDecoderMatcher[Int]("offset")

object LimitQueryParamMatcher extends QueryParamDecoderMatcher[Int]("limit")

trait CustomServerError
object X extends CustomServerError
class Http4sServer(userRepo: UserRepo, articleRepo: ArticlesRepo, tagsRepo: TagsRepo){
  object dsl extends Http4sDsl[IO] {}

  import dsl._

  val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  def getAuthUserFromHeader(authHeader: String): IO[Option[UserResponse]] = {
    UserResponse.getEmailAndPass(authHeader).map(loginUser => userRepo.verify(loginUser).map(maybeUserInfo => maybeUserInfo.map(userInfo => UserResponse.build(loginUser, userInfo).user))(ec).toIO).sequence.map(_.flatten)
  }

    def authUser: Kleisli[IO, Request[IO],Either[CustomServerError,UserResponse]] = Kleisli { request: Request[IO] =>
      val header: Option[Header.Raw] = request.headers.get(CIString("Authorization")).map(_.toRaw)
      header match {
        case Some(h) =>
          getAuthUserFromHeader(h.value).map(_.toRight(X))
        case None => IO(Left(X))
      }
    }

  def onAuthFailure: AuthedRoutes[CustomServerError, IO] = Kleisli { req: AuthedRequest[IO, CustomServerError] =>
    // for any requests' auth failure we return 401
    req.req match {
      case _ =>
        OptionT.pure[IO](
          Response[IO](
            status = Status.Unauthorized
          )
        )
    }
  }
  def authApp = AuthMiddleware(authUser, onAuthFailure)

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
    CORS.DefaultCORSConfig.copy(allowCredentials = true, allowedOrigins = _ => true)

  val httpApp = Router("/" -> app).orNotFound
  def run() = {
    BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(Conf.httpPort, Conf.httpHost)
      .withHttpApp(CORS(httpApp, corsConfig))
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
}