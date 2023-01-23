package com.db.doobieImpl

import cats.effect.IO
import com.db.TagsRepo
import doobie.implicits.toSqlInterpolator
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor.Aux

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DoobieTagsRepo(transactor: Aux[IO, Unit]) extends TagsRepo {
  val getAllQuery = sql"""select distinct unnest (tag_list) from article""".query[String]

  override def getAll(): Future[Set[String]] =
    getAllQuery.stream.compile.toList.transact(transactor).unsafeToFuture()(cats.effect.unsafe.implicits.global).map {
      tagList =>
        tagList.toSet
    }

}
