package com.db

import com.models.auth.{ FullUser, LoginUser, NewUser, UserData, UserInfo, UserWithEncryptedPassword }

import scala.concurrent.Future

trait UserRepo {

  def get(email: String): Future[Option[UserInfo]]
  def save(user: UserWithEncryptedPassword): Future[Int]

  def getHash(email: String): Future[String]

  def update(user: FullUser, email: String): Future[Int]

}
