package com

import cats.effect.IO
import com.db.{ RepoManager, TransactorUtils }
import com.http.{ AkkaServer, Http4sService }
import doobie.util.transactor.Transactor.Aux

import scala.concurrent.ExecutionContext.Implicits.global

object Main {
  def main(args: Array[String]) = {
    val xa: Aux[IO, Unit] = TransactorUtils.transactor
    val repoManager       = RepoManager(xa)
    args.headOption match {
      case Some("akka")   => new AkkaServer(repoManager).run()
      case Some("4s")     => new Http4sService(repoManager).run()
      case invalidCommand => println(s"invalidCommand = ${invalidCommand}")
    }
  }
}
