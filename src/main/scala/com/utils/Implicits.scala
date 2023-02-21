package com.utils

import cats.effect.IO

import scala.concurrent.Future

object Implicits {
  implicit class FutureOps[T](v: Future[T]) {
    val toIO = IO.fromFuture(IO(v))
  }

}
