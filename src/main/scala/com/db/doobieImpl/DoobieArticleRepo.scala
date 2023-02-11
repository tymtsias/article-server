package com.db.doobieImpl

import cats.effect.IO
import com.db.ArticlesRepo
import com.models.{Article, CreateArticleModel, CreatingArticleAdditionalInfo}
import doobie.implicits._
import doobie.util.transactor.Transactor.Aux
import doobie.implicits.javasql._
import doobie.postgres.implicits._
import cats.effect.unsafe.implicits.{global => catsGlobal}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class DoobieArticleRepo(transactor: Aux[IO, Unit]) extends ArticlesRepo {
  override def get(tag: Option[String],
                   author: Option[String],
                   favorited: Option[Boolean],
                   offset: Int,
                   limit: Int): Future[List[Article]] =
    getQuery(tag, author, favorited, offset, limit).stream.compile.toList.transact(transactor).unsafeToFuture()(catsGlobal)

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

  def insertQuery(userEmail: String, entity: CreateArticleModel, info: CreatingArticleAdditionalInfo) = {
    sql"""insert
         |	into
         |	article (user_id,
         |	slug,
         |	title,
         |	description,
         |	body,
         |	tag_list,
         |	created_at,
         |	updated_at,
         |	favorited,
         |	favorites_count,
         |	"following")
         |values ((select id from users where email= ${userEmail}),
         |${info.slug},
         |${entity.title},
         |${entity.description},
         |${entity.body},
         |${entity.tagList},
         |${info.date},
         |${info.date},
         |${info.favorited},
         |${info.favoritesCount},
         |${info.following});""".stripMargin
  }.update

  def yourFeedQuery(limit: Int, offset: Int) = {
    sql"""select
         |	slug,
         |	title,
         |	description,
         |	body,
         |	tag_list,
         |	created_at,
         |	updated_at,
         |	favorited,
         |	favorites_count,
         |	u.bio,
         |	u.username,
         |	u.image,
         |	"following"
         |from
         |	article
         |left join users u on
         |	u.id = user_id
         |where
         |	"following" = true
         |order by
         |	updated_at desc
         |         offset ${offset}
         |limit ${limit};
         """.stripMargin
  }.query[Article]
  def save (userEmail: String, entity: CreateArticleModel)(implicit ec: ExecutionContext): Future[CreatingArticleAdditionalInfo] = {
    val info = CreatingArticleAdditionalInfo("slug", LocalDateTime.now(), true, 0, true)
    insertQuery(userEmail, entity, info).run.transact(transactor).unsafeToFuture()(catsGlobal).map(_ => info)
  }

  override def yourFeed(offset: Int, limit: Int, userEmail: String): Future[List[Article]] = yourFeedQuery(offset = offset, limit = limit).stream.take(limit).compile.toList.transact(transactor).unsafeToFuture()
}
