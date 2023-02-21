package com.db.doobieImpl

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.db.UserRepo
import com.models.auth.{ FullUser, UserInfo, UserWithEncryptedPassword }
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor.Aux

import scala.concurrent.Future

class DoobieUserRepo(transactor: Aux[IO, Unit]) extends UserRepo {
  def saveQuery(user: UserWithEncryptedPassword): Update0 =
    sql"""insert into users (bio, email, image, username, "password") values ('', ${user.email}, '', ${user.username}, ${user.password});""".update
  override def save(user: UserWithEncryptedPassword): Future[Int] =
    saveQuery(user).run.transact(transactor).unsafeToFuture()

  override def getHash(email: String): Future[String] =
    sql"""select "password" from users where email = $email"""
      .query[String]
      .unique
      .transact(transactor)
      .unsafeToFuture()
  def updateQuery(user: FullUser, email: String) =
    sql"""update users set email = ${user.email}, "password" = ${user.password}, username = ${user.username}, bio = ${user.bio}, image = ${user.image} where email = $email;"""
  override def update(user: FullUser, email: String): Future[Int] =
    updateQuery(user, email).update.run.transact(transactor).unsafeToFuture()

  override def get(email: String): Future[Option[UserInfo]] =
    sql"SELECT bio, username, image from users WHERE email = $email"
      .query[UserInfo]
      .option
      .transact(transactor)
      .unsafeToFuture()
}
