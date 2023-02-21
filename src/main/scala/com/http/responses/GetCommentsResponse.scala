package com.http.responses

import com.models.Comment

case class GetCommentsResponse(comments: List[Comment])
