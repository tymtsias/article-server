package com.http

import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import com.Conf
import com.db.{ ArticlesRepo, CommentsRepo, FavoritesRepo, FollowRepo, TagsRepo, UserRepo }
import com.http.requests.{
  ChangeArticleRequest,
  ChangeUserRequest,
  CreateArticleRequest,
  CreateCommentRequest,
  LoginUserRequest,
  NewUserRequest
}
import com.http.responses.{
  ArticlesResponse,
  CommonArticleResponse,
  CreateCommentResponse,
  CreatingArticleResponse,
  GetCommentsResponse,
  TagsResponse,
  UserProfileResponse,
  UserResponse
}
import com.models.ChangeArticle
import com.models.auth.{ UserData, UserWithEncryptedPassword }
import com.utils.Decoders._
import com.utils.Encoders._
import com.utils.Implicits._
import com.utils.PasswordManager
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware._
import org.http4s.server.{ AuthMiddleware, Router }
import org.typelevel.ci.CIString

import scala.concurrent.ExecutionContext
object TagQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("tag")

object AuthorQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("author")

object FavoriedQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Boolean]("favorited")

object OffsetQueryParamMatcher extends QueryParamDecoderMatcher[Int]("offset")

object LimitQueryParamMatcher extends QueryParamDecoderMatcher[Int]("limit")

trait CustomServerError
object X extends CustomServerError
class Http4sServer(userRepo: UserRepo,
                   articleRepo: ArticlesRepo,
                   tagsRepo: TagsRepo,
                   favoritesRepo: FavoritesRepo,
                   commentsRepo: CommentsRepo,
                   followRepo: FollowRepo)(implicit ec: ExecutionContext)
    extends Http4sDsl[IO] {

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
    authNotRequiredRoutes.run(r.req)
  }
  val authNotRequiredRoutes = HttpRoutes.of[IO] {
    case GET -> Root / "articles" / slug / "comments" => {
      commentsRepo.get(slug, None).toIO.flatMap { comments =>
        Ok(GetCommentsResponse(comments).asJson.noSpaces)
      }
    }
    case request @ POST -> Root / "users" / "login" =>
      request
        .as[LoginUserRequest]
        .flatMap { loginUser =>
          userRepo
            .getHash(loginUser.user.email)
            .toIO
            .flatMap { hash =>
              val passwordIsMatched = PasswordManager.checkPassword(loginUser.user.password, hash)
              if (passwordIsMatched) {
                userRepo.get(loginUser.user.email).toIO.flatMap {
                  case Some(userInfo) =>
                    Ok(
                      UserResponse
                        .build(loginUser.user.email, userInfo)
                        .asJson
                        .noSpaces
                    )
                  case None => IO(Response(InternalServerError))
                }
              } else {
                IO(Response(Unauthorized))
              }
            }
        }

    case GET -> Root / "profiles" / username => {
      followRepo.get(username, None).toIO.flatMap { userProfile =>
        Ok(UserProfileResponse(userProfile).asJson.noSpaces)
      }
    }
    case GET -> Root / "articles" / slug =>
      articleRepo.find(slug, None).toIO.flatMap {
        case Some(article) => Ok(CommonArticleResponse(article).asJson.noSpaces)
        case None          => IO(Response(NotFound.status))
      }
    case request @ POST -> Root / "users" =>
      request
        .as[NewUserRequest]
        .flatMap { newUser =>
          userRepo
            .save(UserWithEncryptedPassword.encrypt(newUser.user))
            .toIO
            .flatMap(
              _ =>
                Ok(
                  UserResponse
                    .build(newUser.user)
                    .asJson
                    .noSpaces
              )
            )
        }

    case GET -> Root / "articles" :? TagQueryParamMatcher(tag) +& AuthorQueryParamMatcher(author) +& FavoriedQueryParamMatcher(
          favorited
        ) +& OffsetQueryParamMatcher(offset) +& LimitQueryParamMatcher(limit) =>
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
    case req @ POST -> Root / "articles" / "" as user =>
      req.req.decode[CreateArticleRequest] { req =>
        articleRepo.save(user.email, req.article).toIO.flatMap { info =>
          Created(CreatingArticleResponse.build(user, req.article, info).asJson.noSpaces)
        }
      }

    case request @ PUT -> Root / "user" as user =>
      request.req.decode[ChangeUserRequest] { userRequest =>
        userRepo.update(userRequest.user, user.email).toIO.flatMap { _ =>
          Ok(UserResponse.build(userRequest.user).asJson.noSpaces)
        }
      }

    case GET -> Root / "articles" / "feed" :? LimitQueryParamMatcher(limit) +& OffsetQueryParamMatcher(offset) as user =>
      articleRepo.yourFeed(offset = offset, limit = limit, user.email).toIO.flatMap { articlesList =>
        Ok(ArticlesResponse(articlesList, articlesList.size).asJson.noSpaces)
      }

    case POST -> Root / "articles" / slug / "favorite" as user =>
      val futureArticle = for {
        _       <- favoritesRepo.favorite(slug = slug, email = user.email)
        article <- articleRepo.find(slug, userEmail = Some(user.email))
      } yield article

      futureArticle.toIO.flatMap {
        case Some(article) => Ok(CommonArticleResponse(article).asJson.noSpaces)
        case None          => IO(Response(NotFound.status))
      }

    case GET -> Root / "articles" / slug as user =>
      articleRepo.find(slug, Some(user.email)).toIO.flatMap {
        case Some(article) => Ok(CommonArticleResponse(article).asJson.noSpaces)
        case None          => IO(Response(NotFound.status))
      }
    case request @ PUT -> Root / "articles" / slug as user => {
      request.req.decode[ChangeArticleRequest] { req =>
        articleRepo.checkPermissions(slug, user.email).toIO.flatMap { allowed =>
          if (allowed)
            articleRepo.update(req.article, slug).flatMap(_ => articleRepo.find(slug, Some(user.email))).toIO.flatMap {
              case Some(article) => Ok(CommonArticleResponse(article).asJson.noSpaces)
              case None          => IO(Response(NotFound.status))
            } else IO(Response(Unauthorized))
        }
      }
    }
    case DELETE -> Root / "articles" / slug as user => {
      articleRepo.checkPermissions(slug, user.email).toIO.flatMap { allowed =>
        if (allowed) articleRepo.delete(slug).toIO.flatMap(_ => Ok())
        else IO(Response(Unauthorized))
      }
    }

    case GET -> Root / "articles" / slug / "comments" as user => {
      commentsRepo.get(slug, Some(user.email)).toIO.flatMap { comments =>
        Ok(GetCommentsResponse(comments).asJson.noSpaces)
      }
    }
    case request @ POST -> Root / "articles" / slug / "comments" as user => {
      request.req.decode[CreateCommentRequest] { body =>
        commentsRepo.create(slug, body.comment.body, user.email).toIO.flatMap { comment =>
          Ok(CreateCommentResponse(comment).asJson.noSpaces)
        }
      }
    }
    case DELETE -> Root / "articles" / slug / "comments" / IntVar(id) as user => {
      commentsRepo.delete(id, user.email).toIO.flatMap {
        case true  => Ok()
        case false => IO(Response(Unauthorized))
      }
    }

    case GET -> Root / "profiles" / username as user => {
      followRepo.get(username, Some(user.username)).toIO.flatMap { userProfile =>
        Ok(UserProfileResponse(userProfile).asJson.noSpaces)
      }
    }
    case POST -> Root / "profiles" / username / "follow" as user => {
      followRepo.follow(followed = username, follower = user.username).toIO.flatMap { _ =>
        followRepo.get(username, Some(user.username)).toIO.flatMap { userProfile =>
          Ok(UserProfileResponse(userProfile).asJson.noSpaces)
        }
      }
    }
    case DELETE -> Root / "profiles" / username / "follow" as user => {
      followRepo.unfollow(followed = username, follower = user.username).toIO.flatMap { _ =>
        followRepo.get(username, Some(user.username)).toIO.flatMap { userProfile =>
          Ok(UserProfileResponse(userProfile).asJson.noSpaces)
        }
      }
    }

    case DELETE -> Root / "articles" / slug / "favorite" as user =>
      val futureArticle = for {
        _       <- favoritesRepo.unfavorite(slug = slug, email = user.email)
        article <- articleRepo.find(slug, userEmail = Some(user.email))
      } yield article

      futureArticle.toIO.flatMap {
        case Some(article) => Ok(CommonArticleResponse(article).asJson.noSpaces)
        case None          => IO(Response(NotFound.status))
      }

    case GET -> Root / "articles" :? TagQueryParamMatcher(tag) +& AuthorQueryParamMatcher(author) +& FavoriedQueryParamMatcher(
          favorited
        ) +& OffsetQueryParamMatcher(offset) +& LimitQueryParamMatcher(limit) as user =>
      articleRepo
        .get(user.email, tag, author, favorited, offset, limit)
        .toIO
        .flatMap { articlesList =>
          Ok(ArticlesResponse(articlesList, articlesList.size).asJson.noSpaces)
        }
  }
  val corsConfig =
    CORSConfig.default
      .withAllowCredentials(true)
      .withAllowedOrigins(_ => true)

  val service = authMiddleware(authApp) <+> authNotRequiredRoutes
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
