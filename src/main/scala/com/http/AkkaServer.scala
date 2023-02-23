package com.http

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.{ BadRequest, Created, NotFound, Unauthorized }
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ GenericHttpCredentials, HttpChallenge, HttpCredentials, OAuth2BearerToken }
import akka.http.scaladsl.server.Directives.{ complete, path, _ }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.{ Credentials, DebuggingDirectives }
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import com.Conf
import com.db._
import com.http.requests._
import com.http.responses._
import com.models.auth.{ UserData, UserWithEncryptedPassword }
import io.circe.syntax.EncoderOps
import com.utils.Decoders._
import com.utils.Encoders._
import com.utils.PasswordManager
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax._

import scala.concurrent.Future
import scala.io.StdIn

class AkkaServer(repoManager: RepoManager) extends FailFastCirceSupport {

  implicit val system = ActorSystem(Behaviors.empty, "my-system")
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.executionContext

  val RepoManager(userRepo, articleRepo, tagsRepo, favoritesRepo, commentsRepo, followRepo) = repoManager

  val authNotRequiredRoutes =
    cors() {
      concat(
        get {
          path("articles" / Segment / "comments") { slug =>
            onSuccess(commentsRepo.get(slug, None)) { comments =>
              complete(GetCommentsResponse(comments).asJson)
            }
          }
        },
        post {
          path("users" / "login") {
            entity(as[LoginUserRequest]) {
              loginUser =>
                val passwordIsMatched = userRepo
                  .getHash(loginUser.user.email)
                  .map { hash =>
                    PasswordManager.checkPassword(loginUser.user.password, hash)
                  }
                onSuccess(passwordIsMatched) {
                  case true =>
                    onSuccess(userRepo.get(loginUser.user.email)) {
                      case Some(userInfo) =>
                        complete(
                          UserResponse
                            .build(loginUser.user.email, userInfo)
                            .asJson
                        )
                      case None => complete(BadRequest)
                    }
                  case false => complete(Unauthorized)
                }
            }
          }
        },
        get {
          path("profiles" / Segment) { username =>
            onSuccess(followRepo.get(username, None)) { author =>
              complete(UserProfileResponse(author).asJson)
            }
          }
        },
        post {
          path("users") {
            entity(as[NewUserRequest]) { newUserRequest =>
              onSuccess(userRepo.save(UserWithEncryptedPassword.encrypt(newUserRequest.user))) { _ =>
                complete(UserResponse.build(newUserRequest.user).asJson)
              }
            }
          }
        },
        get {
          path("articles") {
            parameters(
              "tag".optional,
              "author".optional,
              "favorited".as[Boolean].optional,
              "offset".as[Int],
              "limit".as[Int]
            ) { (tag, author, favorited, offset, limit) =>
              onSuccess(articleRepo.get(tag, author, favorited, offset, limit)) { articles =>
                complete(ArticlesResponse(articles, articles.size).asJson)
              }
            }
          }
        },
        get {
          path("tags") {
            onSuccess(tagsRepo.getAll()) { tags =>
              complete(TagsResponse(tags.toSeq).asJson)
            }
          }
        }
      )
    }

  val getArticleRouteAuth =
    cors() {
      authenticateOrRejectWithChallenge(getAuthUserFromCredentials _) { user =>
        get {
          path("articles" / Segment) { slug =>
            pathEnd {
              onSuccess(articleRepo.find(slug, Some(user.email))) {
                case Some(value) => complete(CommonArticleResponse(value).asJson)
                case None        => complete(NotFound)
              }
            }
          }
        }
      }
    }

  val getArticleRouteNoAuth =
    cors() {
      get {
        path("articles" / Segment) { slug =>
          pathEnd {
            onSuccess(articleRepo.find(slug, None)) {
              case Some(value) => complete(CommonArticleResponse(value).asJson)
              case None        => complete(NotFound)
            }
          }
        }
      }
    }

  val challenge = HttpChallenge("MyAuth", Some("MyRealm"))
  def getAuthUserFromCredentials(credentials: Option[HttpCredentials]): Future[AuthenticationResult[UserData]] = {
    credentials match {
      case Some(GenericHttpCredentials(_, token, _)) =>
        UserResponse
          .getEmail(token)
          .map { email =>
            userRepo
              .get(email)
              .map(maybeUserInfo => maybeUserInfo.map(userInfo => UserResponse.build(email, userInfo).user))
          }
          .getOrElse(Future.successful(None))
          .map(_.toRight(challenge))
      case None => Future.successful(Left(challenge))
      case Some(OAuth2BearerToken(token)) =>
        UserResponse
          .getEmail(token)
          .map { email =>
            userRepo
              .get(email)
              .map(maybeUserInfo => maybeUserInfo.map(userInfo => UserResponse.build(email, userInfo).user))
          }
          .getOrElse(Future.successful(None))
          .map(_.toRight(challenge))
    }
  }

  val authedRoutes =
    Route.seal {
      cors() {
        authenticateOrRejectWithChallenge(getAuthUserFromCredentials _) { user =>
          ignoreTrailingSlash {
            concat(
              get {
                path("articles" / "feed") {
                  parameters("offset".as[Int], "limit".as[Int]) { (offset, limit) =>
                    onSuccess(articleRepo.yourFeed(offset = offset, limit = limit, user.email)) { articles =>
                      complete(ArticlesResponse(articles, articles.size).asJson)
                    }
                  }
                }
              },
              get {
                path("user") {
                  complete(UserResponse(user).asJson)
                }
              },
              post {
                path("articles" / "") {
                  entity(as[CreateArticleRequest]) { articleRequest =>
                    onSuccess(articleRepo.save(user.email, articleRequest.article)) { articleAdditionalInfo =>
                      complete(
                        Created,
                        CreatingArticleResponse.build(user, articleRequest.article, articleAdditionalInfo).asJson
                      )
                    }
                  }
                }
              },
              put {
                path("user") {
                  entity(as[ChangeUserRequest]) { changeUserRequest =>
                    onSuccess(userRepo.update(changeUserRequest.user, user.email)) { _ =>
                      complete(UserResponse.build(changeUserRequest.user).asJson)
                    }
                  }
                }
              },
              post {
                path("articles" / Segment / "favorite") { slug =>
                  val futureArticle = for {
                    _       <- favoritesRepo.favorite(slug = slug, email = user.email)
                    article <- articleRepo.find(slug, userEmail = Some(user.email))
                  } yield article

                  onSuccess(futureArticle) {
                    case Some(article) => complete(CommonArticleResponse(article).asJson)
                    case None          => complete(NotFound)
                  }
                }
              },
              get {
                path("articles") {
                  path(Segment) { slug =>
                    onSuccess(articleRepo.find(slug, Some(user.email))) {
                      case Some(value) => complete(CommonArticleResponse(value).asJson)
                      case None        => complete(NotFound)
                    }
                  }
                }
              },
              put {
                path("articles" / Segment) {
                  slug =>
                    entity(as[ChangeArticleRequest]) { changeArticleRequest =>
                      onSuccess(articleRepo.checkPermissions(slug, user.email)) {
                        case true =>
                          onSuccess(
                            articleRepo
                              .update(changeArticleRequest.article, slug)
                              .flatMap(_ => articleRepo.find(slug, Some(user.email)))
                          ) {
                            case Some(value) => complete(CommonArticleResponse(value).asJson)
                            case None        => complete(NotFound)
                          }
                        case false => complete(Unauthorized)
                      }
                    }
                }
              },
              delete {
                path("articles" / Segment) { slug =>
                  onSuccess(articleRepo.checkPermissions(slug, user.email)) {
                    case true =>
                      onSuccess(articleRepo.delete(slug)) {
                        complete()
                      }
                    case false => complete(Unauthorized)
                  }
                }
              },
              get {
                path("articles" / Segment / "comments") { slug =>
                  onSuccess(commentsRepo.get(slug, Some(user.email))) { comments =>
                    complete(GetCommentsResponse(comments).asJson)
                  }
                }
              },
              post {
                path("articles" / Segment / "comments") { slug =>
                  entity(as[CreateCommentRequest]) { commentRequest =>
                    onSuccess(commentsRepo.create(slug, commentRequest.comment.body, user.email)) { comment =>
                      complete(CreateCommentResponse(comment).asJson)
                    }
                  }
                }
              },
              delete {
                path("articles" / Segment / "comments") { slug =>
                  path(IntNumber) { id =>
                    onSuccess(commentsRepo.delete(id, user.email)) {
                      case true  => complete()
                      case false => complete(Unauthorized)
                    }
                  }
                }
              },
              get {
                path("profiles" / Segment) { username =>
                  onSuccess(followRepo.get(username, Some(user.username))) { userProfile =>
                    complete(UserProfileResponse(userProfile).asJson)
                  }
                }
              },
              post {
                path("profiles" / Segment / "follow") { username =>
                  onSuccess(
                    followRepo
                      .follow(followed = username, follower = user.username)
                      .flatMap(_ => followRepo.get(username, Some(user.username)))
                  ) { userProfile =>
                    complete(UserProfileResponse(userProfile).asJson)
                  }
                }
              },
              delete {
                path("profiles" / Segment / "follow") { username =>
                  onSuccess(
                    followRepo
                      .unfollow(followed = username, follower = user.username)
                      .flatMap(_ => followRepo.get(username, Some(user.username)))
                  ) { userProfile =>
                    complete(UserProfileResponse(userProfile).asJson)
                  }
                }
              },
              delete {
                path("articles" / Segment / "favorite") { slug =>
                  val futureArticle = for {
                    _       <- favoritesRepo.unfavorite(slug = slug, email = user.email)
                    article <- articleRepo.find(slug, userEmail = Some(user.email))
                  } yield article

                  onSuccess(futureArticle) {
                    case Some(value) => complete(CommonArticleResponse(value).asJson)
                    case None        => complete(NotFound)
                  }
                }
              },
              get {
                path("articles") {
                  parameters(
                    "tag".optional,
                    "author".optional,
                    "favorited".as[Boolean].optional,
                    "offset".as[Int],
                    "limit".as[Int]
                  ) { (tag, author, favorited, offset, limit) =>
                    onSuccess(articleRepo.get(user.email, tag, author, favorited, offset, limit)) { articles =>
                      complete(ArticlesResponse(articles, articles.size).asJson)
                    }
                  }
                }
              }
            )
          }
        }
      }
    }

  def run() = {
    val routesWithDebug = DebuggingDirectives.logRequestResult("Client ReST", Logging.InfoLevel)(
      Route
        .seal(concat(getArticleRouteAuth, concat(authNotRequiredRoutes, concat(getArticleRouteNoAuth, authedRoutes))))
    )
    val bindingFuture = Http()
      .newServerAt(Conf.httpHost, Conf.httpPort)
      .bind(routesWithDebug)
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}
