import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{DecodingFailure, Decoder, Encoder}

package object domain {
  implicit val anyDecoder: Decoder[Any] = Decoder.instance(cursor => {
    cursor.focus match {
      case Some(x) => Right(x)
      case None    => Left(DecodingFailure("Could not parse Any value", List()))
    }
  })

  implicit val prometheusQueryResultDecoder: Decoder[PrometheusQueryResult] =
    deriveDecoder

  implicit val prometheusQueryResultEncoder: Encoder[PrometheusQueryResult] =
    deriveEncoder

  implicit val prometheusDataDecoder: Decoder[PrometheusData] =
    deriveDecoder

  implicit val prometheusDataEncoder: Encoder[PrometheusData] =
    deriveEncoder

  implicit val resultBisDecoder: Encoder[Result] =
    deriveEncoder

  implicit val resultBisEncoder: Decoder[Result] =
    Decoder.instance(cursor =>
      for {
        value <- cursor.downField("value").as[List[Any]]
        valuesCleaned = value.map(_.toString.replace("\"", ""))
      } yield Result(valuesCleaned)
    )
}
