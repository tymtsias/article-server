package com.utils

import at.favre.lib.crypto.bcrypt.BCrypt

object PasswordManager {

  def toHash(password: String): String = BCrypt.withDefaults().hashToString(12, password.toCharArray)

  def checkPassword(password: String, hash: String): Boolean = BCrypt.verifyer().verify(password.toCharArray, hash).verified

}
