package com

import cats.effect.IOApp
import com.db.TransactorUtils
import com.db.doobieImpl.{DoobieArticleRepo, DoobieCommentsRepo, DoobieFavoritesRepo, DoobieFollowRepo, DoobieTagsRepo, DoobieUserRepo}
import com.http.Http4sServer

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends IOApp {
  def run(args: List[String]) = {
    val xa          = TransactorUtils.transactor
    val userRepo    = new DoobieUserRepo(xa)
    val articleRepo = new DoobieArticleRepo(xa)
    val tagsRepo    = new DoobieTagsRepo(xa)
    val favoritesRepo = new DoobieFavoritesRepo(xa)
    val commentsRepo = new DoobieCommentsRepo(xa)
    val followRepo = new DoobieFollowRepo(xa)

    val server = new Http4sServer(userRepo, articleRepo, tagsRepo, favoritesRepo, commentsRepo, followRepo)
    server.run()
  }

}
