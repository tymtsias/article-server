package com.models

import com.models.auth.Author

import java.sql.Timestamp

case class Comment (id: Int, createdAt: Timestamp, updatedAt: Timestamp, body: String, author: Author)
