package utils.silhouette

import db.Users
import models.User
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import com.mohiva.play.silhouette.api.LoginInfo
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import Implicits._
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordInfoDAO @Inject() (val users: Users) extends DelegableAuthInfoDAO[PasswordInfo] {

  def add(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] =
    update(loginInfo, authInfo)

  def find(loginInfo: LoginInfo): Future[Option[PasswordInfo]] =
    Future.successful(users.findByEmail(loginInfo)).map {
      case Some(user) if user.emailconfirmed => Some(user.password)
      case _ => None
    }

  def remove(loginInfo: LoginInfo): Future[Unit] = Future.successful(users.remove(loginInfo))

  def save(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] =
    find(loginInfo).flatMap {
      case Some(_) => update(loginInfo, authInfo)
      case None => add(loginInfo, authInfo)
    }

  def update(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] =
    Future.successful(users.findByEmail(loginInfo)).map {
      case Some(user) => {
        users.save(user.copy(password = authInfo))
        authInfo
      }
      case _ => throw new Exception("PasswordInfoDAO - update : the user must exists to update its password")
    }

}