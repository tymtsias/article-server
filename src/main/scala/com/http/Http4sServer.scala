package com.http

import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import com.Conf
import com.db.{ArticlesRepo, TagsRepo, UserRepo}
import com.http.requests.{CreateArticleRequest, LoginUserRequest, NewUserRequest}
import com.http.responses.{ArticlesResponse, CreatingArticleResponse, TagsResponse, UserResponse}
import com.models.auth.UserData
import com.utils.Decoders._
import com.utils.Encoders._
import com.utils.Implicits._
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

import scala.concurrent.ExecutionContext
object TagQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("tag")

object AuthorQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("author")

object FavoriedQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Boolean]("favorited")

object OffsetQueryParamMatcher extends QueryParamDecoderMatcher[Int]("offset")

object LimitQueryParamMatcher extends QueryParamDecoderMatcher[Int]("limit")

trait CustomServerError
object X extends CustomServerError
class Http4sServer(userRepo: UserRepo, articleRepo: ArticlesRepo, tagsRepo: TagsRepo)(implicit ec: ExecutionContext) extends Http4sDsl[IO] {

  def getAuthUserFromHeader(authHeader: String): IO[Option[UserData]] = {
    UserResponse.getEmail(authHeader).map { email =>
      userRepo
        .get(email)
        .map(maybeUserInfo => maybeUserInfo.map(userInfo => UserResponse.build(email, userInfo).user))(ec)
        .toIO
    } match {
      case Some(value) => value
      case None        => IO.pure(None)
    }
  }

  def authUser: Kleisli[IO, Request[IO], Either[CustomServerError, UserData]] = Kleisli { request: Request[IO] =>
    val header: Option[Header.Raw] = request.headers.get(CIString("Authorization")).map(_.head)
    header match {
      case Some(h) =>
        getAuthUserFromHeader(h.value.drop(6)).map(_.toRight(X))
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
        .as[LoginUserRequest]
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
        .as[NewUserRequest]
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

    case PUT -> Root / "users" => ???
    case GET -> Root / "articles" :? TagQueryParamMatcher(tag) +& AuthorQueryParamMatcher(author) +& FavoriedQueryParamMatcher(
          favorited) +& OffsetQueryParamMatcher(offset) +& LimitQueryParamMatcher(limit) =>
      articleRepo
        .get(tag, author, favorited, offset, limit)
        .toIO
        .flatMap { articlesList =>
          Ok(ArticlesResponse(articlesList, articlesList.size).asJson.noSpaces)
        }

    case GET -> Root / "tags" =>
      tagsRepo
        .getAll()
        .toIO
        .flatMap(tags => Ok(TagsResponse(tags.toSeq).asJson.noSpaces))
  }
  def authMiddleware = AuthMiddleware(authUser, onAuthFailure)

  val authApp: AuthedRoutes[UserData, IO] = AuthedRoutes.of {
    case GET -> Root / "user" as user => Ok(UserResponse(user).asJson.noSpaces)
    case req@POST -> Root / "articles" / "" as user =>
      req.req.decode[CreateArticleRequest] { req =>
        articleRepo.save(user.email, req.article).toIO.flatMap { info =>
          Created(CreatingArticleResponse.build(user, req.article, info).asJson.noSpaces)
        }
      }

    case GET -> Root / "articles" / "feed" :? LimitQueryParamMatcher(limit) +& OffsetQueryParamMatcher(offset) as user =>
      articleRepo.yourFeed(offset = offset, limit = limit, user.email).toIO.flatMap { articlesList =>
      Ok(ArticlesResponse(articlesList, articlesList.size).asJson.noSpaces)
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
