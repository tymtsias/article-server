package com.models

case class CreateArticleModel(title: String, description: String, body: String, tagList: List[String])
