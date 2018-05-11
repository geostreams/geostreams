package utils.silhouette

import play.api.mvc.Controller
import play.api.Logger
import play.api.i18n.I18nSupport
import com.mohiva.play.silhouette.api.{ Environment, Silhouette }
import com.mohiva.play.silhouette.api.actions._
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import com.mohiva.play.silhouette.api.LoginInfo
import com.google.inject.Guice
import db.postgres.PostgresUsers
import models.User
import play.api.db.Database
import com.mohiva.play.silhouette.password.BCryptPasswordHasher

trait AuthCookieController extends Controller with I18nSupport {
  def silhouette: Silhouette[CookieEnv]
  def env: Environment[CookieEnv] = silhouette.env
  def SecuredAction: SecuredActionBuilder[CookieEnv] = silhouette.SecuredAction
  def UnsecuredAction = silhouette.UnsecuredAction
  def UserAwareAction = silhouette.UserAwareAction

  implicit def securedRequest2User[A](implicit request: SecuredRequest[CookieEnv, A]): User = request.identity
  implicit def userAwareRequest2UserOpt[A](implicit request: UserAwareRequest[CookieEnv, A]): Option[User] = request.identity
}