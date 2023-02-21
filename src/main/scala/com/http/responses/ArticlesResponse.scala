package com.http.responses

import com.models.Article

case class ArticlesResponse(articles: List[Article], articlesCount: Int)
