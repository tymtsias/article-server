package com.http

import cats.effect.unsafe.implicits.global
import com.db.RepoManager

import scala.concurrent.ExecutionContext
import scala.io.StdIn

class Http4sService(repoManager: RepoManager)(implicit ec: ExecutionContext) {
  def run(): Unit = {
    val io = new Http4sServer(repoManager).run().map(_ => StdIn.readLine()).unsafeToFuture()
    StdIn.readLine()
  }
}
