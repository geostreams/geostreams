package controllers

import models._
import db.Users
import utils.silhouette._
import utils.silhouette.Implicits._
import com.mohiva.play.silhouette.api.{ LoginEvent, LoginInfo, LogoutEvent, SignUpEvent, Silhouette }
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.{ Clock, Credentials, PasswordHasherRegistry }
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.impl.authenticators.BearerTokenAuthenticator
import com.mohiva.play.silhouette.impl.exceptions.{ IdentityNotFoundException, InvalidPasswordException }
import com.mohiva.play.silhouette.api.Authenticator.Implicits._
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.api.util.PasswordInfo

import play.api._
import play.api.mvc._
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.i18n.{ Messages, MessagesApi }
import play.api.libs.json.Json
import utils.Mailer

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import net.ceedubs.ficus.Ficus._
import javax.inject.{ Inject, Singleton }

import views.html.{ auth => viewsAuth }

@Singleton
class UserController @Inject() (
    val silhouette: Silhouette[TokenEnv],
    val messagesApi: MessagesApi,
    userService: UserService,
    authInfoRepository: AuthInfoRepository,
    credentialsProvider: CredentialsProvider,
    tokenService: MailTokenService[MailTokenUser],
    passwordHasherRegistry: PasswordHasherRegistry,
    mailer: Mailer,
    conf: Configuration,
    clock: Clock,
    usersDB: Users
) extends AuthTokenController {

  val signInForm = Form(tuple(
    "identifier" -> email,
    "password" -> nonEmptyText
  ))

  def tokenAuthenticate = UnsecuredAction.async { implicit request =>
    signInForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(Json.toJson("Post data error."))),
      formData => {
        val identifier = formData._1
        val password = formData._2

        usersDB.findByEmail(identifier) match {
          case Some(user) => {
            val passwordInfo: PasswordInfo = user.password

            if ((new BCryptPasswordHasher()).matches(passwordInfo, password)) {
              for {
                authenticator <- env.authenticatorService.create(identifier)
                token <- env.authenticatorService.init(authenticator)
                result <- env.authenticatorService.embed(token, Ok(Json.toJson(user)))
              } yield {
                env.eventBus.publish(LoginEvent(user, request))
                Logger.debug(token)
                result
              }
            } else {
              Future.successful(BadRequest(Json.toJson("invalid password.")))
            }
          }
          case None => Future.successful(BadRequest(Json.toJson("Couldn't find user")))
        }
      }
    )
  }
}

