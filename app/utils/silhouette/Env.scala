package utils.silhouette

import com.mohiva.play.silhouette.api.repositories.AuthenticatorRepository
import com.mohiva.play.silhouette.api.{ Env, LoginInfo }
import com.mohiva.play.silhouette.impl.authenticators.BearerTokenAuthenticator
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import models.User

trait CookieEnv extends Env {
  type I = User
  type A = CookieAuthenticator
}

trait TokenEnv extends Env {
  type I = User
  type A = BearerTokenAuthenticator
}