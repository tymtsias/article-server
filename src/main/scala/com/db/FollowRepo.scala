package com.db

import com.models.auth.Author

import scala.concurrent.Future

trait FollowRepo {
  def get(username: String, follower: Option[String]): Future[Author]

  def follow(follower: String, followed: String): Future[Int]

  def unfollow(follower: String, followed: String): Future[Int]

}
