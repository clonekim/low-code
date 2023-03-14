package scratch

import io.javalin.http.Context
import io.javalin.http.HttpStatus
import java.sql.Connection
import java.sql.ResultSet

fun templateHandle(ctx: Context) {

    /* val model = ctx.bodyAsClass(JavaModel::class.java)

     model.template?.let {
         ctx.render("${it.lowercase()}${FREEMARKER_EXT}",
             mapOf("columns" to model.columns,
                 ""
             ))
     } ?: Exception("invalid request")*/

}

fun createDataSourceHandler(ctx: Context) {
    val db = ctx.bodyAsClass(Database::class.java)
    store[db.name!!] = makeDatasource(db.driverClassName!!, db.jdbcUrl!!, db.username, db.password)
    ctx.status(HttpStatus.OK)
}



fun getDataSourceHandler(ctx: Context) {
    ctx.json(store.keys())
}



fun queryHandle(ctx: Context) {
    val query = ctx.bodyAsClass(Query::class.java)
    logger.debug( "Query => {}", query)

    val runQueryClosure = fun (conn: Connection, sql: String): ResultSet {
        return conn
            .createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
            .executeQuery(sql).also { it.fetchSize = 200 }
    }


    ctx.header("x-db-identity")?.let { key ->

        val data = connection(key, query.fetchSize) { conn ->

            when {
                query.statement != null -> runQueryClosure(conn, query.statement!!)

                query.table != null -> runQueryClosure(conn, "SELECT * FROM ${query.table}")

                else -> throw Exception("invalid query")
            }
        }

        ctx.json(data)

    } ?: throw Exception("cannot find datasource")

}

