package com.db.doobieImpl

import cats.effect.IO
import com.db.ArticlesRepo
import com.models.Article
import doobie.implicits._
import doobie.util.transactor.Transactor.Aux
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.pgisimplicits._
import cats._
import cats.implicits._
import cats.effect._
import cats.effect.implicits._
import scala.concurrent.Future

class DoobieArticleRepo(transactor: Aux[IO, Unit]) extends ArticlesRepo {
  override def get(tag: Option[String],
                   author: Option[String],
                   favorited: Option[Boolean],
                   offset: Int,
                   limit: Int): Future[List[Article]] =
    getQuery(tag, author, favorited, offset, limit).stream.compile.toList.transact(transactor).unsafeToFuture()

  def getQuery(tag: Option[String],
               author: Option[String],
               favorited: Option[Boolean],
               offset: Int,
               limit: Int): doobie.Query0[Article] = {

    def condition = {
      if (tag.isEmpty && author.isEmpty && favorited.isEmpty) sql""
      else {
        val xC       = if (tag.isDefined) tag.map(tag => sql"${tag} = any (tag_list) ").getOrElse(sql"") else sql""
        val yCPrefix = if (tag.isDefined && author.isDefined) sql"and " else sql""
        val yC       = if (author.isDefined) author.map(author => sql"u.username = ${author} ").getOrElse(sql"") else sql""
        val zCPrefix = if ((favorited.isDefined || tag.isDefined) && author.isDefined) sql"and " else sql""
        val zC =
          if (favorited.isDefined) favorited.map(favorited => sql"favorited = ${favorited} ").getOrElse(sql"")
          else sql""
        sql"where " ++ xC ++ yCPrefix ++ yC ++ zCPrefix ++ zC
      }
    }

    val sqlToExecute = sql"""select
         |  slug,
         |  title,
         |  description,
         |  body,
         |  tag_list,
         |  created_at,
         |  updated_at,
         |  favorited,
         |  favorites_count,
         |  u.bio,
         |  u.username,
         |  u.image,
         |  "following"
         |from
         |  article
         |left join users u on
         |  u.id = user_id
         |""".stripMargin ++ condition ++ sql" offset ${offset} limit ${limit};"
    sqlToExecute.query[Article]
  }
}
