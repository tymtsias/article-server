package com.db

import cats.effect._
import doobie.Transactor
import doobie.util.transactor.Transactor.Aux

object TransactorUtils {
  val transactor: Aux[IO, Unit] = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql:articles",
    "tym",
    "13579"
  )

}
