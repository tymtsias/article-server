package com.db

import cats.effect.IO
import com.db.doobieImpl._
import doobie.util.transactor.Transactor.Aux

case class RepoManager(
    userRepo: UserRepo,
    articleRepo: ArticlesRepo,
    tagsRepo: TagsRepo,
    favoritesRepo: FavoritesRepo,
    commentsRepo: CommentsRepo,
    followRepo: FollowRepo
)

object RepoManager {
  def apply(xa: Aux[IO, Unit]): RepoManager = new RepoManager(
    userRepo = new DoobieUserRepo(xa),
    articleRepo = new DoobieArticleRepo(xa),
    tagsRepo = new DoobieTagsRepo(xa),
    favoritesRepo = new DoobieFavoritesRepo(xa),
    commentsRepo = new DoobieCommentsRepo(xa),
    followRepo = new DoobieFollowRepo(xa)
  )
}
