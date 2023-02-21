package com.db

import com.models.auth.{ FullUser, LoginUser, NewUser, UserData, UserInfo }

import scala.concurrent.Future

trait UserRepo {

  def get(email: String): Future[Option[UserInfo]]
  def save(user: NewUser): Future[Int]

  def verify(loginUser: LoginUser): Future[Option[UserInfo]]

  def update(user: FullUser, email: String): Future[Int]

}
