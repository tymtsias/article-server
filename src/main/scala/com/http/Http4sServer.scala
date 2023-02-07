package com.http

import cats.data.Kleisli
import cats.effect._
import com.Conf
import com.Implicits._
import com.db.{ArticlesRepo, TagsRepo, UserRepo}
import com.models.auth.{LoginUserModel, NewUserModel, UserResponse, UserResponseModel}
import com.models.{ArticleModel, CreateArticleModel, CreateArticleRequest, CreatingArticleResponse, TagsResponse}
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware._
import org.http4s.server.{AuthMiddleware, Router}
import org.typelevel.ci.CIString
import cats.syntax._
import cats.implicits._

import scala.concurrent.ExecutionContext
object TagQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("tag")

object AuthorQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("author")

object FavoriedQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Boolean]("favorited")

object OffsetQueryParamMatcher extends QueryParamDecoderMatcher[Int]("offset")

object LimitQueryParamMatcher extends QueryParamDecoderMatcher[Int]("limit")

trait CustomServerError
object X extends CustomServerError
class Http4sServer(userRepo: UserRepo, articleRepo: ArticlesRepo, tagsRepo: TagsRepo) extends Http4sDsl[IO] {
  val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  def getAuthUserFromHeader(authHeader: String): IO[Option[UserResponseModel]] = {
    UserResponse.getEmail(authHeader).map { email =>
      userRepo
        .get(email)
        .map(maybeUserInfo => maybeUserInfo.map(userInfo => UserResponse.build(email, userInfo)))(ec)
        .toIO
    } match {
      case Some(value) => value
      case None        => IO.pure(None)
    }
  }

  def authUser: Kleisli[IO, Request[IO], Either[CustomServerError, UserResponse]] = Kleisli { request: Request[IO] =>
    val header: Option[Header.Raw] = request.headers.get(CIString("Authorization")).map(_.head)
    header match {
      case Some(h) =>
        getAuthUserFromHeader(h.value).map(_.map(_.user).toRight(X))
      case None =>
        IO(Left(X))
    }
  }

  def onAuthFailure: AuthedRoutes[CustomServerError, IO] = Kleisli { r =>
    app.run(r.req)
  }
  val app = HttpRoutes.of[IO] {
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
                    .build(loginUser.user.email, userInfo)
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
  def authMiddleware = AuthMiddleware(authUser, onAuthFailure)

  val authApp: AuthedRoutes[UserResponse, IO] = AuthedRoutes.of {
    case GET -> Root / "user" as user => Ok(UserResponseModel(user).asJson.noSpaces)
    case  req@POST -> Root / "articles" / "" as user =>
      req.req.decode[CreateArticleRequest] { req =>
        articleRepo.save(user.email, req.article).toIO.flatMap { info =>
          Created(CreatingArticleResponse.build(user, req.article, info).asJson.noSpaces)
        }
      }
  }
  val corsConfig =
    CORSConfig.default
      .withAllowCredentials(true)
      .withAllowedOrigins(_ => true)

  val service = app <+> authMiddleware(authApp)
  val httpApp = Router("/" -> service).orNotFound
  def run() = {
    BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(Conf.httpPort, Conf.httpHost)
      .withHttpApp(CORS(httpApp, corsConfig))
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
}
