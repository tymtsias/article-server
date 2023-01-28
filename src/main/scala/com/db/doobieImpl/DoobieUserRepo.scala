package com.db.doobieImpl

import cats.effect.IO
import com.db.UserRepo
import com.models.auth.{LoginUser, NewUser, UserInfo, UserResponse}
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor.Aux

import scala.concurrent.Future

class DoobieUserRepo(transactor: Aux[IO, Unit]) extends UserRepo {
  def saveQuery(user: NewUser): Update0 =
    sql"""insert into users (bio, email, image, username, "password") values ('', ${user.email}, '', ${user.username}, ${user.password});""".update
  override def save(user: NewUser): Future[Int] = saveQuery(user).run.transact(transactor).unsafeToFuture()

  def verifyQuery(loginUser: LoginUser): doobie.Query0[UserInfo] =
    sql"""select bio, userName, image from users where email = ${loginUser.email} and password = ${loginUser.password}"""
      .query[UserInfo]
  override def verify(loginUser: LoginUser): Future[Option[UserInfo]] =
    verifyQuery(loginUser).stream.take(1L).compile.toList.map(l => l.headOption).transact(transactor).unsafeToFuture()

  override def update(user: UserResponse): Future[Int] = ???
}
