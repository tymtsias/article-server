package com.managers

import com.models.auth.UserResponse

import scala.concurrent.Future

trait AuthManager {

  def signIn(user: UserResponse): Future[Option[UserResponse]]

  def signUp(user: UserResponse): Future[UserResponse]

  def getCurrentUser(): Future[UserResponse]

  def updateUser(user: UserResponse): Future[UserResponse]

}
