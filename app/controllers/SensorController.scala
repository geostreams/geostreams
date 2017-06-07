package controllers

import java.sql.Statement
import javax.inject._

import model.{GeometryModel, SensorModel}
import play.api.data.Forms._
import play.api.data._
import play.api.db.Database
import play.api.i18n._
import play.api.libs.functional.syntax._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Json._

case class SensorData(name: String, age: Int)

/**
  * Sensors or locations are the topmost data structure in the data model. Sensors contain streams and streams contain
  * datapoints.
  */
class SensorController @Inject()(db: Database)(implicit val messagesApi: MessagesApi) extends Controller with I18nSupport {

  implicit val geometryReads: Reads[GeometryModel] = (
    (JsPath \ "type").read[String] and
      (JsPath \ "coordinates").read[JsValue]
    ) (GeometryModel.apply _)

  implicit val geometryWrite = Json.writes[GeometryModel]

  implicit val sensorReads: Reads[SensorModel] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "geometry").read[GeometryModel] and
      (JsPath \ 'properties).read[JsValue]
    ) (SensorModel.apply _)

  implicit val sensorWrite = Json.writes[SensorModel]

  val sensorForm = Form(
    mapping(
      "name" -> text,
      "age" -> number
    )(SensorData.apply)(SensorData.unapply)
  )

  def sensorFormGet = Action { implicit request =>
    Ok(views.html.sensor.form(sensorForm))
  }

  def sensorFormPost = Action { implicit request =>
    sensorForm.bindFromRequest.fold(
      formWithErrors => {
        // binding failure, you retrieve the form containing errors:
        BadRequest(views.html.sensor.form(formWithErrors))
      },
      sensorData => {
        /* binding success, you get the actual value. */
        /* flashing uses a short lived cookie */
        Redirect(routes.SensorController.sensorFormGet()).flashing("success" -> ("Successful " + sensorData.toString))
      }
    )
  }

  /**
    * Create sensor.
    *
    * @return
    */
  def sensorCreate = Action(BodyParsers.parse.json) { implicit request =>
    val sensorResult = request.body.validate[SensorModel]
    sensorResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      sensor => {
        // connection will be closed at the end of the block
        db.withConnection { conn =>
          val ps = conn.prepareStatement("INSERT INTO sensors(name, geog, created, metadata) " +
            "VALUES(?, CAST(ST_GeomFromGeoJSON(?) AS geography), NOW(), CAST(? AS json));",
            Statement.RETURN_GENERATED_KEYS)
          ps.setString(1, sensor.name)
          ps.setString(2, Json.stringify(Json.toJson(sensor.geometry)))
          ps.setString(3, Json.stringify(sensor.properties))
          ps.executeUpdate()
          val rs = ps.getGeneratedKeys
          rs.next()
          val id = rs.getInt(1)
          rs.close()
          ps.close()
          Ok(Json.obj("status" -> "OK", "id" -> id))
        }
      }
    )
  }

  /**
    * Retrieve sensor. Set `min_start_time` and `max_start_time` based on streams belonging to this sensor.
    * TODO: simplify query by setting `min_start_time` and `max_start_time` when updating statistics on sensors and streams.
    *
    * @param id
    * @return
    */
  def sensorGet(id: Int) = Action {
    val sensor = getSensor(id)
    Ok(Json.obj("status" -> "OK", "sensor" -> sensor))
  }

  /**
    * Update `properties` element of sensor.
    * @param id
    * @return new sensor definition
    */
  def updateSensorMetadata(id: Int) = Action(parse.json) { implicit request =>
    request.body.validate[(JsObject)].map {
      case (body) =>
        // connection will be closed at the end of the block
        db.withConnection { conn =>
          val query = "SELECT row_to_json(t, true) AS my_sensor FROM (" +
            "SELECT metadata As properties FROM sensors " +
            "WHERE gid=?) AS t"
          val st = conn.prepareStatement(query)
          st.setInt(1, id.toInt)
          val rs = st.executeQuery()
          var sensorData = ""
          while (rs.next()) {
            sensorData += rs.getString(1)
          }
          rs.close()
          st.close()

          val oldDataJson = Json.parse(sensorData).as[JsObject]

          val jsonTransformer = (__ \ 'properties).json.update(
            __.read[JsObject].map { o => o ++ body }
          )
          val updatedJSON: JsObject = oldDataJson.transform(jsonTransformer).getOrElse(oldDataJson)

          val query2 = "UPDATE sensors SET metadata = CAST(? AS json) WHERE gid = ?"
          val st2 = conn.prepareStatement(query2)
          st2.setString(1, Json.stringify((updatedJSON \ "properties").getOrElse(Json.obj())))
          st2.setInt(2, id)
          st2.executeUpdate()
          st2.close()
          val sensor = getSensor(id)
          Ok(Json.obj("status" -> "OK", "sensor" -> sensor))
        }
    }.recoverTotal {
      e => BadRequest("Detected error:" + JsError.toJson(e))
    }
  }

  def getSensorStatistics(id: String) = Action {
    NotImplemented
  }

  def getSensorStreams(id: String) = Action {
    NotImplemented
  }

  def updateStatisticsSensor(id: String) = Action {
    NotImplemented
  }

  def updateStatisticsStreamSensor() = Action {
    NotImplemented
  }

  def searchSensors(geocode: Option[String], sensor_name: Option[String]) = Action {
    NotImplemented
  }

  def deleteSensor(id: String) = Action {
    db.withConnection { conn =>
      val query = "DELETE FROM datapoints USING streams WHERE stream_id IN (SELECT gid FROM streams WHERE sensor_id = ?);" +
        "DELETE FROM streams WHERE gid IN (SELECT gid FROM streams WHERE sensor_id = ?);" +
        "DELETE FROM sensors where gid = ?;"
      val st = conn.prepareStatement(query)
      st.setInt(1, id.toInt)
      st.setInt(2, id.toInt)
      st.setInt(3, id.toInt)
      st.executeUpdate()
      st.close()
      Ok(Json.obj("status" -> "OK"))
    }
  }

  private[this] def getSensor(id: Int): JsValue = {
    db.withConnection { conn =>
      // TODO store start time, end time and parameter list in the row and update them when the update sensor endpoint is called.
      // Then simplify this query to not calculate them on the fly.
      val query = "WITH stream_info AS (" +
        "SELECT sensor_id, start_time, end_time, unnest(params) AS param FROM streams WHERE sensor_id=?)" +
        "SELECT row_to_json(t, true) AS my_sensor FROM (" +
        "SELECT gid As id, name, to_char(created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, " +
        "'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry, " +
        "to_char(min(stream_info.start_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS min_start_time, " +
        "to_char(max(stream_info.end_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') as max_end_time, " +
        "array_agg(distinct stream_info.param) as parameters " +
        "FROM sensors " +
        "LEFT OUTER JOIN stream_info ON stream_info.sensor_id = sensors.gid " +
        "WHERE sensors.gid=?" +
        "GROUP BY gid) AS t"
      val st = conn.prepareStatement(query)
      st.setInt(1, id)
      st.setInt(2, id)
      val rs = st.executeQuery()
      rs.next()
      val data = rs.getString(1)
      rs.close()
      st.close()
      Json.parse(data)
    }
  }
}
