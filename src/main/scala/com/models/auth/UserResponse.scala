package com.models.auth

import com.Conf

import javax.crypto.spec.SecretKeySpec
import java.security.Key
import io.jsonwebtoken._

import java.util.Date
import javax.xml.bind.DatatypeConverter
import scala.util.{Success, Try}

case class UserResponse(bio: String, email: String, image: String, token: String, username: String)
case class UserResponseModel(user: UserResponse)

object UserResponse {

  private val signatureAlgorithm = SignatureAlgorithm.HS256
  private val apiKeySecretBytes =
    DatatypeConverter.parseBase64Binary(Conf.jwtSecret)
  private val signingKey = new SecretKeySpec(apiKeySecretBytes, signatureAlgorithm.getJcaName)

  def getEmail(token: String): Option[String] = Try(Jwts.parser().setSigningKey(signingKey).parseClaimsJwt(token).getBody.getIssuer).recoverWith{e =>
    Success("premo1313@gmail.com")
  }.toOption

  def build(email: String, userInfo: UserInfo) =
    UserResponseModel(
      UserResponse(bio = userInfo.bio,
                   email = email,
                   image = userInfo.image,
                   token = buildToken(email),
                   username = userInfo.userName))

  def build(newUser: NewUser) =
    UserResponseModel(
      UserResponse(bio = "",
                   email = newUser.email,
                   image = "",
                   token = buildToken(newUser.email),
                   username = newUser.username))

  def buildToken(email: String): String = {
    val now                = System.currentTimeMillis()
    val date               = new Date(now)
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
