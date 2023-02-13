package com.db.doobieImpl

import cats.effect.IO
import com.db.FavoritesRepo
import doobie.implicits.toSqlInterpolator
import doobie.util.transactor.Transactor.Aux
import doobie.implicits._
import cats.effect.unsafe.implicits.{global => catsGlobal}
import doobie.util.log.LogHandler
import doobie.postgres.implicits._

import scala.concurrent.{ExecutionContext, Future}

class DoobieFavoritesRepo(transactor: Aux[IO, Unit]) extends FavoritesRepo{

  override def favorite(slug: String, email: String)(implicit ec: ExecutionContext): Future[Unit] = favoriteQuery(slug, email).run.transact(transactor).unsafeToFuture()(catsGlobal).map(_ => ())


  def favoriteQuery(slug: String, email: String) =
    sql""" insert into favorites (article_id, user_id) values ((select id from article where slug = ${slug}), (select id from users  where email = ${email}));""".update

  def unfavoriteQuery(slug: String, email: String) =
    sql""" delete from favorites where article_id = (select id from article where slug = ${slug}) and user_id = (select id from users  where email = ${email});""".update

  override def unfavorite(slug: String, email: String)(implicit ec: ExecutionContext): Future[Unit] = unfavoriteQuery(slug, email).run.transact(transactor).unsafeToFuture()(catsGlobal).map(_ => ())

}
