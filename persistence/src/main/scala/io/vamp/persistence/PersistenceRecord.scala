package io.vamp.persistence

import java.time.OffsetDateTime

import io.vamp.common.json.{ OffsetDateTimeSerializer, SerializationFormat }
import io.vamp.common.notification.NotificationProvider
import io.vamp.common.{ Artifact, Config, Namespace, NamespaceProvider }
import io.vamp.model.Model
import io.vamp.persistence.notification.UnknownDataFormatException
import org.json4s.Formats
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write

import scala.util.Try

object PersistenceRecord {

  def apply(name: String, kind: String): PersistenceRecord = PersistenceRecord(Model.version, Model.uuid, OffsetDateTime.now(), name, kind, None)

  def apply(name: String, kind: String, artifact: String): PersistenceRecord = PersistenceRecord(Model.version, Model.uuid, OffsetDateTime.now(), name, kind, Option(artifact))
}

case class PersistenceRecord(version: String, instance: String, timestamp: OffsetDateTime, name: String, kind: String, artifact: Option[String])

abstract class PersistenceRecordTransformer(namespace: Namespace) {

  def timeDependent: Boolean = false

  def read(input: String): String

  def write(input: String): String

}

trait PersistenceRecordMarshaller {
  this: NamespaceProvider ⇒

  protected val transformersPath = "vamp.persistence.transformers.classes"

  private lazy val transformers = {
    val transformerClasses = if (Config.has(transformersPath)(namespace)()) Config.stringList(transformersPath)() else Nil
    transformerClasses.map { clazz ⇒
      Class.forName(clazz).getConstructor(classOf[Namespace]).newInstance(namespace).asInstanceOf[PersistenceRecordTransformer]
    }
  }

  lazy val timeDependent: Boolean = transformers.exists(_.timeDependent)

  def marshallRecord(record: PersistenceRecord): String = {
    val content = write(record)(SerializationFormat(OffsetDateTimeSerializer))
    transformers.foldLeft[String](content)((input, transformer) ⇒ transformer.write(input))
  }

  def unmarshallRecord(source: String): PersistenceRecord = {
    val input = transformers.foldRight[String](source)((transformer, source) ⇒ transformer.read(source))
    implicit val format: Formats = SerializationFormat(OffsetDateTimeSerializer)
    Serialization.read[PersistenceRecord](input)
  }
}

trait PersistenceDataReader extends PersistenceRecordMarshaller with PersistenceMarshaller {
  this: PersistenceApi with NamespaceProvider with NotificationProvider ⇒

  protected def dataSet(artifact: Artifact, kind: String): Artifact

  protected def dataDelete(name: String, kind: String): Unit

  protected def dataRead(data: String): PersistenceRecord = {
    val record = Try(unmarshallRecord(data)).getOrElse(throwException(UnknownDataFormatException("")))
    record.artifact match {
      case Some(content) ⇒ unmarshall(record.kind, content).map(a ⇒ dataSet(a, record.kind)).getOrElse(throwException(UnknownDataFormatException(record.kind)))
      case None          ⇒ dataDelete(record.name, record.kind)
    }
    record
  }
}
