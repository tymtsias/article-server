package com.db.doobieImpl

import cats.effect.IO
import com.db.ArticlesRepo
import com.models.{Article, CreateArticleModel, CreatingArticleAdditionalInfo}
import doobie.implicits._
import doobie.util.transactor.Transactor.Aux
import doobie.implicits.javasql._
import doobie.postgres.implicits._
import cats.effect.unsafe.implicits.{global => catsGlobal}
import doobie.implicits._


import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class DoobieArticleRepo(transactor: Aux[IO, Unit]) extends ArticlesRepo {
  override def get(tag: Option[String],
                   author: Option[String],
                   favorited: Option[Boolean],
                   offset: Int,
                   limit: Int): Future[List[Article]] =
    getQuery(tag, author, favorited, offset, limit).stream.compile.toList
      .transact(transactor)
      .unsafeToFuture()(catsGlobal)

  def condition(tag: Option[String],
                author: Option[String],
                favorited: Option[Boolean]) = {
    if (tag.isEmpty && author.isEmpty && favorited.isEmpty) sql""
    else {
      val xC = if (tag.isDefined) tag.map(tag => sql"${tag} = any (a.tag_list) ").getOrElse(sql"") else sql""
      val yCPrefix = if (tag.isDefined && author.isDefined) sql"and " else sql""
      val yC = if (author.isDefined) author.map(author => sql"u.username = ${author} ").getOrElse(sql"") else sql""
      val zCPrefix = if ((favorited.isDefined || tag.isDefined) && author.isDefined) sql"and " else sql""
      val zC =
        if (favorited.isDefined) favorited.map(favorited => sql"a.favorited = ${favorited} ").getOrElse(sql"")
        else sql""
      sql"where " ++ xC ++ yCPrefix ++ yC ++ zCPrefix ++ zC
    }
  }
  def getQuery(tag: Option[String],
               author: Option[String],
               favorited: Option[Boolean],
               offset: Int,
               limit: Int): doobie.Query0[Article] = {

    val sqlToExecute = sql"""
                            |select
                            |  a.slug,
                            |  a.title,
                            |  a.description,
                            |  a.body,
                            |  a.tag_list,
                            |  a.created_at,
                            |  a.updated_at,
                            |  false,
                            |  a.favorites_count,
                            |  u.bio,
                            |  u.username,
                            |  u.image,
                            |  false
                            |from
                            |  article a
                            |left join users u on
                            |  u.id = user_id""".stripMargin ++ condition(tag, author, favorited) ++ sql" offset ${offset} limit ${limit};"
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
         |	favorites_count)
         |values ((select id from users where email= ${userEmail}),
         |${info.slug},
         |${entity.title},
         |${entity.description},
         |${entity.body},
         |${entity.tagList},
         |${info.date},
         |${info.date},
         |${info.favoritesCount});""".stripMargin
  }.update

  def yourFeedQuery(limit: Int, offset: Int, userEmail: String) = {
    sql"""select
         |	slug,
         |	title,
         |	description,
         |	body,
         |	tag_list,
         |	created_at,
         |	updated_at,
         |	(select count(*) from favorites f2 where f2.user_id = u2.id and f2.article_id = article_id)::int::bool,
         |	favorites_count,
         |	u.bio,
         |	u.username,
         |	u.image,
         |	true
         |from
         |	article
         |left join followers f on user_id = f.followed
         |left join users u on u.id = f.follower
         |left join users u2 on u2.email = $userEmail
         |where f.follower = u2.id
         |order by
         |	updated_at desc
         |         offset ${offset}
         |limit ${limit};
         """.stripMargin
  }.query[Article]
  def save(userEmail: String, entity: CreateArticleModel)(
      implicit ec: ExecutionContext): Future[CreatingArticleAdditionalInfo] = {
    val info = CreatingArticleAdditionalInfo(UUID.randomUUID().toString, LocalDateTime.now(), true, 0, true)
    insertQuery(userEmail, entity, info).run.transact(transactor).unsafeToFuture()(catsGlobal).map(_ => info)
  }

  override def yourFeed(offset: Int, limit: Int, userEmail: String): Future[List[Article]] =
    yourFeedQuery(offset = offset, limit = limit, userEmail = userEmail).stream
      .take(limit)
      .compile
      .toList
      .transact(transactor)
      .unsafeToFuture()

  override def find(slug: String, userEmail: Option[String]): Future[Option[Article]] = {
    val query  = userEmail match {
      case Some(value) => findQueryForExistingUser(slug, value)
      case None => findQuery(slug)
    }
    query.option.transact(transactor).unsafeToFuture()
  }


  def findQueryForExistingUser(slug: String, userEmail: String) = {
    sql"""select
         |	slug,
         |	title,
         |	description,
         |	body,
         |	tag_list,
         |	created_at,
         |	updated_at,
         |	(select count(*) from favorites f2 where f2.user_id = (select id from users where email = $userEmail) and article_id = f2.article_id)::int::bool,
         |	favorites_count,
         |	u.bio,
         |	u.username,
         |	u.image,
         |	(select count(*) from followers f1 where f1.follower = (select id from users where email = $userEmail) and f1.followed = user_id)::int::bool
         |from
         |	article
         |left join users u on u.id = user_id
         |where slug = $slug;
   """.stripMargin
  }.query[Article]

  def findQuery(slug: String) = {
    sql"""select
         |	slug,
         |	title,
         |	description,
         |	body,
         |	tag_list,
         |	created_at,
         |	updated_at,
         |	true,
         |	favorites_count,
         |	u.bio,
         |	u.username,
         |	u.image,
         |	true
         |from
         |	article
         |left join users u on u.id = user_id
         |where slug = $slug;
     """.stripMargin
  }.query[Article]

  override def get(userEmail: String, tag: Option[String], author: Option[String], favorited: Option[Boolean], offset: Int, limit: Int): Future[List[Article]] =
    getForAuthUserQuery(userEmail, tag, author, favorited, offset, limit).stream.compile.toList
      .transact(transactor)
      .unsafeToFuture()(catsGlobal)

  def getForAuthUserQuery(userEmail: String, tag: Option[String], author: Option[String], favorited: Option[Boolean], offset: Int, limit: Int) = {
    val sqlToExecute = sql"""select distinct
         |	a.slug,
         |	a.title,
         |	a.description,
         |	a.body,
         |	a.tag_list,
         |	a.created_at,
         |	a.updated_at,
         |	(select count(*) from favorites f2 where f2.user_id = u2.id and f2.article_id = a.id)::int::bool,
         |	a.favorites_count,
         |	u.bio,
         |	u.username,
         |	u.image,
         |	(select count(*) from followers f where f.follower = u2.id and f.followed = a.user_id)::int::bool
         |from
         |	article a
         |left join followers f on user_id = f.followed
         |left join users u on u.id = f.followed
         |left join users u2 on u2.email = $userEmail
         """.stripMargin ++ condition(tag, author, favorited) ++ sql""" order by updated_at desc offset ${offset} limit ${limit};"""

    sqlToExecute.query[Article]
  }
}
