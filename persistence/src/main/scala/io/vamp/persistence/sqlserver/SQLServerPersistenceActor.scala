package io.vamp.persistence.sqlserver

import io.vamp.common.ClassMapper
import io.vamp.persistence.sql.SqlPersistenceActor

class SQLServerPersistenceActorMapper extends ClassMapper {
  val name = "sqlserver"
  val clazz: Class[_] = classOf[SQLServerPersistenceActor]
}

class SQLServerPersistenceActor extends SqlPersistenceActor {

  def selectStatement(lastId: Long): String = s"SELECT [ID], [Record] FROM [$table] WHERE [ID] > $lastId ORDER BY [ID] ASC"

  def insertStatement(): String = s"insert into [$table] ([Record]) values (?)"
}
