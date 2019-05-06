package utils.silhouette

import com.mohiva.play.silhouette.api.repositories.AuthenticatorRepository
import com.mohiva.play.silhouette.api.{ Env, LoginInfo }
import com.mohiva.play.silhouette.impl.authenticators.BearerTokenAuthenticator
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import models.User
import org.joda.time.DateTime
import scala.collection.mutable
import scala.concurrent.Future

object DummyRepository extends AuthenticatorRepository[BearerTokenAuthenticator] {
  private val authenticators = mutable.Set[BearerTokenAuthenticator]()

  override def find(id: String): Future[Option[BearerTokenAuthenticator]] = {
    Future.successful(authenticators.find(_.id == id))
  }

  override def add(authenticator: BearerTokenAuthenticator): Future[BearerTokenAuthenticator] = {
    authenticators += authenticator
    Future.successful(authenticator)
  }

  override def update(authenticator: BearerTokenAuthenticator): Future[BearerTokenAuthenticator] = {
    this.remove(authenticator.id)
    this.add(authenticator)
  }

  override def remove(id: String): Future[Unit] = {
    authenticators.find(_.id == id).forall(authenticators.remove)
    Future.successful()
  }

}