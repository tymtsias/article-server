package com.db

import com.models.auth.{LoginUser, NewUser, UserInfo, UserResponse}

import scala.concurrent.Future

trait UserRepo {

  def get(email: String): Future[Option[UserInfo]]
  def save(user: NewUser): Future[Int]

  def verify(loginUser: LoginUser): Future[Option[UserInfo]]

  def update(user: UserResponse): Future[Int]

}
