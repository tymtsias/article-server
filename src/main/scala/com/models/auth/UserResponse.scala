package com.models.auth

import com.Conf

import javax.crypto.spec.SecretKeySpec
import java.security.Key
import io.jsonwebtoken._

import java.util.Date
import javax.xml.bind.DatatypeConverter

case class UserResponse(bio: String, email: String, image: String, token: String, username: String)
case class UserResponseModel(user: UserResponse)

object UserResponse {

  def build(loginUser: LoginUser, userInfo: UserInfo) =
    UserResponseModel(
      UserResponse(bio = userInfo.bio,
                   email = loginUser.email,
                   image = userInfo.image,
                   token = buildToken(loginUser.email),
                   username = userInfo.userName))

  def build(newUser: NewUser) =
    UserResponseModel(
      UserResponse(bio = "",
                   email = newUser.email,
                   image = "",
                   token = buildToken(newUser.email),
                   username = newUser.username))

  def buildToken(email: String) = {
    val signatureAlgorithm = SignatureAlgorithm.HS256
    val now                = System.currentTimeMillis()
    val date               = new Date(now)
    val apiKeySecretBytes =
      DatatypeConverter.parseBase64Binary(Conf.jwtSecret)
    val signingKey = new SecretKeySpec(apiKeySecretBytes, signatureAlgorithm.getJcaName)
    val builder = Jwts
      .builder()
      .setId("id")
      .setIssuedAt(date)
      .setSubject("hello")
      .setIssuer(email)
      .signWith(signatureAlgorithm, signingKey)
      .setExpiration(new Date(now + 1000000000000L))

    builder.compact()
  }

}
