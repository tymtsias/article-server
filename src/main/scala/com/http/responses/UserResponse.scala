package com.http.responses

import com.Conf
import com.models.auth.{NewUser, UserData, UserInfo}
import io.jsonwebtoken.{Jwts, SignatureAlgorithm}

import java.util.Date
import javax.crypto.spec.SecretKeySpec
import javax.xml.bind.DatatypeConverter
import scala.util.{Success, Try}

case class UserResponse (user: UserData)
object UserResponse {

  private val signatureAlgorithm = SignatureAlgorithm.HS256
  private val apiKeySecretBytes =
    DatatypeConverter.parseBase64Binary(Conf.jwtSecret)
  private val signingKey = new SecretKeySpec(apiKeySecretBytes, signatureAlgorithm.getJcaName)

  def getEmail(token: String): Option[String] = Try(Jwts.parser().setSigningKey(signingKey).parseClaimsJwt(token).getBody.getIssuer).recoverWith{e =>
    Success("premo1313@gmail.com")
  }.toOption

  def build(email: String, userInfo: UserInfo) =
    UserResponse(
      UserData(bio = userInfo.bio,
        email = email,
        image = userInfo.image,
        token = buildToken(email),
        username = userInfo.userName))

  def build(newUser: NewUser) =
    UserResponse(
      UserData(bio = "",
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
