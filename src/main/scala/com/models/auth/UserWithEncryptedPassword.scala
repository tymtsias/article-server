package com.models.auth

import com.utils.PasswordManager

case class UserWithEncryptedPassword (email: String, password: String, username: String)

object UserWithEncryptedPassword {

  def encrypt(newUser: NewUser): UserWithEncryptedPassword = UserWithEncryptedPassword(newUser.email, PasswordManager.toHash(newUser.password), newUser.username)

}
