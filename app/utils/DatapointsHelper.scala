package utils

import org.joda.time.DateTime

object DatapointsHelper {
  // check the date is the in the specified season, time format like: 2017-06-15T18:30:50Z
  def checkSeason(season: String, date: String): Boolean = {
    val month = date.slice(5, 7)
    season match {
      case "spring" | "Spring" => month == "03" || month == "04" || month == "05"
      case "summer" | "Summer" => month == "06" || month == "07" || month == "08"
      case _ => false
    }
  }

  def checkSeason(season: String, date: DateTime): Boolean = {
    val month = date.getMonthOfYear()
    season match {
      case "spring" | "Spring" => month == 3 || month == 4 || month == 5
      case "summer" | "Summer" => month == 6 || month == 7 || month == 8
      case _ => false
    }
  }
}
import org.joda.time.format.{ DateTimeFormat, ISODateTimeFormat }
import org.joda.time.{ DateTime, IllegalInstantException }
import play.api.Logger
import play.api.libs.json._

object DatapointsHelper {
  def timeBins(time: String, startTime: DateTime, endTime: DateTime): Map[String, JsObject] = {
    val iso = ISODateTimeFormat.dateTime()
    val result = collection.mutable.HashMap.empty[String, JsObject]

    time.toLowerCase match {
      case "decade" => {
        var counter = new DateTime((startTime.getYear / 10) * 10, 1, 1, 0, 0, 0)
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val year = counter.getYear
          val date = new DateTime(year + 5, 7, 1, 12, 0, 0)
          result.put(year.toString, Json.obj("year" -> year, "date" -> iso.print(date)))
          counter = counter.plusYears(10)
        }
      }
      case "lustrum" => {
        var counter = new DateTime((startTime.getYear / 5) * 5, 1, 1, 0, 0, 0)
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val year = counter.getYear
          val date = new DateTime(year + 2, 7, 1, 12, 0, 0)
          result.put(year.toString, Json.obj("year" -> year, "date" -> iso.print(date)))
          counter = counter.plusYears(5)
        }
      }
      case "year" => {
        var counter = new DateTime(startTime.getYear, 1, 1, 0, 0, 0)
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val year = counter.getYear
          val date = new DateTime(year, 7, 1, 12, 0, 0, 0)
          result.put(year.toString, Json.obj("year" -> year, "date" -> iso.print(date)))
          counter = counter.plusYears(1)
        }
      }
      case "semi" => {
        var counter = startTime
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val year = counter.getYear
          if (counter.getMonthOfYear < 7) {
            result.put(year + " spring", Json.obj(
              "year" -> year,
              "date" -> iso.print(new DateTime(year, 3, 1, 12, 0, 0))
            ))

          } else {
            result.put(year + " summer", Json.obj(
              "year" -> year,
              "date" -> iso.print(new DateTime(year, 9, 1, 12, 0, 0))
            ))
          }
          counter = counter.plusMonths(6)
        }
      }
      case "season" => {
        var counter = startTime
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val year = counter.getYear
          if ((counter.getMonthOfYear < 3) || (counter.getMonthOfYear == 3 && counter.getDayOfMonth < 21)) {
            result.put(year + " winter", Json.obj(
              "year" -> year,
              "date" -> iso.print(new DateTime(year, 2, 1, 12, 0, 0))
            ))
          } else if ((counter.getMonthOfYear < 6) || (counter.getMonthOfYear == 6 && counter.getDayOfMonth < 21)) {
            result.put(year + " spring", Json.obj(
              "year" -> year,
              "date" -> iso.print(new DateTime(year, 5, 1, 12, 0, 0))
            ))
          } else if ((counter.getMonthOfYear < 9) || (counter.getMonthOfYear == 9 && counter.getDayOfMonth < 21)) {
            result.put(year + " summer", Json.obj(
              "year" -> year,
              "date" -> iso.print(new DateTime(year, 8, 1, 12, 0, 0))
            ))
          } else if ((counter.getMonthOfYear < 12) || (counter.getMonthOfYear == 12 && counter.getDayOfMonth < 21)) {
            result.put(year + " fall", Json.obj(
              "year" -> year,
              "date" -> iso.print(new DateTime(year, 11, 1, 12, 0, 0))
            ))
          } else {
            result.put(year + " winter", Json.obj(
              "year" -> year,
              "date" -> iso.print(new DateTime(year, 2, 1, 12, 0, 0))
            ))
          }
          counter = counter.plusMonths(3)
        }
      }
      case "month" => {
        var counter = startTime
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val label = DateTimeFormat.forPattern("YYYY MMMM").print(counter)
          val year = counter.getYear
          val month = counter.getMonthOfYear
          val date = new DateTime(year, month, 15, 12, 0, 0, 0)
          result.put(label, Json.obj("year" -> year, "month" -> month, "date" -> iso.print(date)))
          counter = counter.plusMonths(1)
        }
      }
      case "day" => {
        var counter = startTime
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val label = DateTimeFormat.forPattern("YYYY-MM-dd").print(counter)
          val year = counter.getYear
          val month = counter.getMonthOfYear
          val day = counter.getDayOfMonth
          val date = new DateTime(year, month, day, 12, 0, 0, 0)
          result.put(label, Json.obj("year" -> year, "month" -> month, "day" -> day, "date" -> iso.print(date)))
          counter = counter.plusDays(1)
        }
      }
      case "hour" => {
        var counter = startTime
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val label = DateTimeFormat.forPattern("YYYY-MM-dd HH").print(counter)
          val year = counter.getYear
          val month = counter.getMonthOfYear
          val day = counter.getDayOfMonth
          val hour = counter.getHourOfDay
          try {
            val date = new DateTime(year, month, day, hour, 30, 0, 0)
            result.put(label, Json.obj("year" -> year, "month" -> month, "day" -> day, "hour" -> hour, "date" -> iso.print(date)))
          } catch {
            case e: IllegalInstantException => Logger.debug("Invalid Instant Exception", e)
          }
          counter = counter.plusHours(1)
        }
      }
      case "minute" => {
        var counter = startTime
        while (counter.isBefore(endTime) || counter.isEqual(endTime)) {
          val label = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm").print(counter)
          val year = counter.getYear
          val month = counter.getMonthOfYear
          val day = counter.getDayOfMonth
          val hour = counter.getHourOfDay
          val minute = counter.getMinuteOfHour
          try {
            val date = new DateTime(year, month, day, hour, minute, 30, 0)
            result.put(label, Json.obj("year" -> year, "month" -> month, "day" -> day, "hour" -> hour, "minute" -> minute, "date" -> iso.print(date)))
          } catch {
            case e: IllegalInstantException => Logger.debug("Invalid Instant Exception", e)
          }
          counter = counter.plusMinutes(1)

        }
      }
      case _ => // do nothing
    }

    result.toMap
  }
}

case class BinHelper(
  depth: Double,
  label: String,
  extras: JsObject,
  timeInfo: JsObject,
  doubles: collection.mutable.ListBuffer[Double] = collection.mutable.ListBuffer.empty[Double],
  array: collection.mutable.HashSet[String] = collection.mutable.HashSet.empty[String],
  strings: collection.mutable.HashSet[String] = collection.mutable.HashSet.empty[String],
  sources: collection.mutable.HashSet[String] = collection.mutable.HashSet.empty[String]
)