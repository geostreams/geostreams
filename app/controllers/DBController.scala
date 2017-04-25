package controllers

import javax.inject.Inject

import play.api.mvc._
import play.api.db._

class DBController @Inject()(db: Database) extends Controller {

  def index = Action {
    var outString = "Stats: \n"
    // play manages connection and calls close() at the end of the block
    db.withConnection { conn =>

      try {
        val stmt = conn.createStatement
        val rs = stmt.executeQuery("SELECT (SELECT COUNT(DISTINCT gid) FROM sensors) AS sensors,(SELECT COUNT(DISTINCT gid) FROM streams) AS streams,(SELECT COUNT(DISTINCT gid) FROM datapoints) AS datapoints")

        while (rs.next()) {
          outString += "\tSensors: " + rs.getString("sensors") + "\n"
          outString += "\tStreams: " + rs.getString("streams") + "\n"
          outString += "\tDatapoints: " + rs.getString("datapoints") + "\n"
        }
      } finally {
        conn.close()
      }
      Ok(outString)
    }
  }

}
