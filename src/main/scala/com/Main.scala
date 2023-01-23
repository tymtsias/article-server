package com

import cats.effect.IOApp
import com.db.TransactorUtils
import com.db.doobieImpl.{DoobieArticleRepo, DoobieTagsRepo, DoobieUserRepo}
import com.http.Http4sServer

object Main extends IOApp {
  def run(args: List[String]) = {
    val xa          = TransactorUtils.transactor
    val userRepo    = new DoobieUserRepo(xa)
    val articleRepo = new DoobieArticleRepo(xa)
    val tagsRepo    = new DoobieTagsRepo(xa)

    val server = new Http4sServer(userRepo, articleRepo, tagsRepo)
    server.run()
  }

}
