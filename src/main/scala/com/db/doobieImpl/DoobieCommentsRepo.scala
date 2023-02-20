package com.db.doobieImpl

import cats.effect.IO
import com.db.CommentsRepo
import com.models.Comment
import doobie.implicits._
import doobie.util.transactor.Transactor.Aux
import doobie.implicits.javasql._
import doobie.postgres.implicits._
import doobie._
import cats.effect.unsafe.implicits.{global => catsGlobal}
import doobie.implicits._

import scala.concurrent.Future

class DoobieCommentsRepo(transactor: Aux[IO, Unit]) extends CommentsRepo {

  def getQueryForUser(slug: String, userEmail: String) = sql"""select
                                                              |	ac.id,
                                                              |	ac.created_at,
                                                              |	ac.updated_at,
                                                              |	ac.body,
                                                              |	u.bio,
                                                              |	u.username,
                                                              |	u.image,
                                                              |	(select count(*) from followers f where f.followed  = ac.user_id and f.follower = (select id from users where email = $userEmail))::int::bool
                                                              |from
                                                              |	article_comments ac
                                                              |left join users u on
                                                              |	ac.user_id = u.id
                                                              |where
                                                              |	ac.article_id = (
                                                              |	select
                                                              |		id
                                                              |	from
                                                              |		article a
                                                              |	where
                                                              |		a.slug = $slug)""".stripMargin.query[Comment]

  def getQueryForUnauthorized(slug: String) =
    sql"""select
         |	ac.id,
         |	ac.created_at,
         |	ac.updated_at,
         |	ac.body,
         |	u.bio,
         |	u.username,
         |	u.image,
         |	false
         |from
         |	article_comments ac
         |left join users u on
         |	ac.user_id = u.id
         |where
         |	article_id = (
         |	select
         |		id
         |	from
         |		article a
         |	where
         |		a.slug = $slug)""".stripMargin.query[Comment]

  def createAndGetIdQuery(slug: String, body: String, userEmail: String) =
    sql"""insert
                                                                                |	into
                                                                                |	article_comments (created_at,
                                                                                |	updated_at,
                                                                                |	body,
                                                                                |	user_id,
                                                                                |	article_id)
                                                                                |values (now(),
                                                                                |now(),
                                                                                |$body,
                                                                                |(select id from users where email = $userEmail),
                                                                                |(select id from article where slug = $slug)) returning id""".stripMargin
      .query[Int]
  def find(id: Int, userEmail: String) =
    sql"""select
         |	ac.id,
         |	ac.created_at,
         |	ac.updated_at,
         |	ac.body,
         |	u.bio,
         |	u.username ,
         |	u.image,
         |	(
         |	select
         |		count(*)
         |	from
         |		followers f
         |	where
         |		f.followed = ac.user_id
         |		and f.follower = (
         |		select
         |			id
         |		from
         |			users
         |		where
         |			email = $userEmail)
         |			)::int::bool
         |from
         |	article_comments
         |ac
         |left join users u on
         |		u.id = ac.user_id
         |where
         |	ac.id = $id""".stripMargin
      .query[Comment]

  def checkPermissionForDeleteQuery(id: Int, userEmail: String) =
    sql"select 1 from article_comments ac left join users u on u.id =ac.user_id where u.email = $userEmail and ac.id = $id"
      .query[Int]

  def deleteQuery(id: Int) = sql"delete from article_comments where id = $id ".update

  override def get(slug: String, maybeUserEmail: Option[String]): Future[List[Comment]] = {
    val queryToExecute = maybeUserEmail match {
      case Some(value) => getQueryForUser(slug, value)
      case None        => getQueryForUnauthorized(slug)
    }
    queryToExecute.stream.compile.toList.transact(transactor).unsafeToFuture()
  }

  override def create(slug: String, body: String, userEmail: String): Future[Comment] =
    createAndGetIdQuery(slug, body, userEmail).unique.flatMap { gotId =>
      find(gotId, userEmail).unique
    }.transact(transactor).unsafeToFuture()

  override def delete(id: Int, userEmail: String): Future[Boolean] =
    checkPermissionForDeleteQuery(id, userEmail).option
      .transact(transactor)
      .flatMap {
        case None => IO(false)
        case _    => deleteQuery(id).run.transact(transactor).map(_ => true)
      }
      .unsafeToFuture()
}
