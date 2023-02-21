package com.db.doobieImpl

import com.db.FollowRepo
import com.models.auth.Author

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

class DoobieFollowRepo(transactor: Aux[IO, Unit]) extends FollowRepo{

  def getQueryForUser(follower: String, followed: String) =
    sql"""select
         |	u.bio,
         |	u.username ,
         |	u.image,
         |	(
         |	select
         |		count(*)
         |	from
         |		followers f1
         |	where
         |		f1.follower = (
         |		select
         |			id
         |		from
         |			users
         |		where
         |			username = $follower)
         |		and f1.followed = u.id)::int::bool
         |from
         |	users u
         |where
         |	u.username = $followed""".stripMargin.query[Author]

  def getQuery(username: String) =
    sql"""select
         |	u.bio,
         |	u.username ,
         |	u.image,
         |	false
         |from
         |	users u
         |where
         |	u.username = $username""".stripMargin.query[Author]

  override def get(username: String, follower: Option[String]): Future[Author] = {
    val queryToExecute =  follower match {
      case Some(value) => getQueryForUser(follower = value, followed = username)
      case None => getQuery(username)
    }
    queryToExecute.unique.transact(transactor).unsafeToFuture()
  }

  def followQuery(follower: String, followed: String) =
    sql"""insert
         |	into
         |	followers (follower,
         |	followed)
         |values (
         |(
         |select
         |	u.id
         |from
         |	users u
         |where
         |	u.username = $follower ),
         |(
         |select
         |	u.id
         |from
         |	users u
         |where
         |	u.username = $followed ));
         |""".stripMargin.update

  override def follow(follower: String, followed: String): Future[Int] = followQuery(follower, followed).run.transact(transactor).unsafeToFuture()


  def unfollowQuery(follower: String, followed: String) =
    sql"""delete
         |from
         |	followers
         |where
         |	follower =
         |	(
         |	select
         |		u.id
         |	from
         |		users u
         |	where
         |		u.username = $follower )
         |	and
         |	followed = (
         |	select
         |		u.id
         |	from
         |		users u
         |	where
         |		u.username = $followed );""".stripMargin.update

  override def unfollow(follower: String, followed: String): Future[Int] = unfollowQuery(follower = follower, followed = followed).run.transact(transactor).unsafeToFuture()
}
