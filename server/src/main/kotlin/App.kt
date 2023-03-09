package scratch

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addResourceOrFileSource
import com.sksamuel.hoplite.addResourceSource
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import freemarker.cache.ClassTemplateLoader
import freemarker.cache.FileTemplateLoader
import freemarker.cache.NullCacheStorage
import freemarker.template.Configuration
import freemarker.template.TemplateMethodModelEx
import freemarker.template.TemplateScalarModel
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.ContentType
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.staticfiles.Location
import io.javalin.rendering.JavalinRenderer
import mu.KotlinLogging
import org.apache.commons.text.CaseUtils
import java.io.BufferedWriter
import java.io.File
import java.io.StringWriter
import java.sql.Connection
import java.sql.ResultSet
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource
import kotlin.collections.set


const val FREEMARKER_EXT = ".ftl"

data class Server(val port: Int, val debug: Boolean = false, val showBanner: Boolean = false)
data class Database(
    var name: String? = null,
    var jdbcUrl: String? = null,
    var driverClassName: String? = null,
    var username: String? = null,
    var password: String? = null,
)

data class Template(val location: String)
data class Config(val server: Server, val database: Database, val template: Template)

data class Query(
    var statement: String? = null,
    var schema: String? = null,
    var table: String? = null,
    var fetchSize: Int? = null
)


data class Column(
    val table: String,
    val name: String,
    var label: String?,
    val size: Int,
    val nullable: Boolean = false,
    val autoIncrement: Boolean = false,
    var sqlType: SQLType? = null,
    val className: String? = null,
    var scale: Scale? = null
)

data class SQLType(val type: Int, val name: String)
data class Scale(val size: Int?, val precision: Int?)
data class SQLModel(var columns: List<Column>? = null, var template: String? = null)

data class JavaModel(
    val useUnderscore: Boolean? = false,
    val jsonFormat: String? = null,
    val jsonProperty: String? = null,
    val column: Column
)

data class JavaClass (
    val className: String,
    val packageName: String,
    val validation: Boolean? = false,
    val builder: Boolean? = false,
    val template: String,
    val properties: List<JavaModel> = mutableListOf()
)

val logger = KotlinLogging.logger { }
val store = ConcurrentHashMap<String, DataSource?>()

class App {
    var config: Config = loadConfig()
    var cfg: Configuration = initTemplate()
}

fun App.start() {

    val app = Javalin.create { javalinConfig ->
        javalinConfig.showJavalinBanner = config.server.showBanner
        javalinConfig.staticFiles.add("/static", Location.CLASSPATH)
        javalinConfig.routing.ignoreTrailingSlashes = true
        javalinConfig.http.defaultContentType = ContentType.JSON


    }.events { event ->
        event.serverStarted {

            val (_, jdbcUrl, driverClassName, username, password) = config.database

            store["default"] = makeDatasource(
                driverClassName = driverClassName!!,
                jdbcUrl = jdbcUrl!!,
                username = username,
                password = password)
        }

    }.routes {
        path("/api") {
            post("/pool", ::poolHandler)
          //  get("/connected", ::connectedHandler)
            post("/table", ::queryHandle)
            post("/template", ::templateHandle)
        }

    }.exception(Exception::class.java) { e, ctx ->

        e.printStackTrace()
        ctx.json(mapOf("status" to 500, "message" to e.message))

    }.start(config.server.port)

    Runtime.getRuntime().addShutdownHook(Thread {
        app.stop()
    })
}



fun App.initTemplate(): Configuration {

    return Configuration(Configuration.VERSION_2_3_32).apply {
        cacheStorage = NullCacheStorage()

        //https://tm8r.hateblo.jp/entry/20110915/1316097034
        setSharedVariable("camel", object : TemplateMethodModelEx {
            override fun exec(arguments: MutableList<Any?>?): Any? {
                if(arguments?.size == 1) {
                    val str = (arguments[0] as TemplateScalarModel).asString
                    return CaseUtils.toCamelCase(str, false, '_')
                }
                else
                    return null
            }
        })

        when {
              config.template.location.startsWith("classpath:") ->  ClassTemplateLoader(App::class.java.classLoader, config.template.location.substring(10))
              config.template.location.startsWith("file:") -> FileTemplateLoader(File( config.template.location.substring(5)))
              else -> throw Exception("invalid templateloader")
          }.also {
            templateLoader = it
        }
    }
}

fun App.registerRenderer() {

    JavalinRenderer.register({ filePath, model, _ ->
        val template = cfg.getTemplate(filePath)

        StringWriter().run {
            BufferedWriter(this).use { out ->
                template.process(model, out)
            }
            toString()
        }

    }, FREEMARKER_EXT)

}

fun App.loadConfig(): Config {
    return ConfigLoaderBuilder.default()
        .addResourceSource("/application.yml", optional = true)
        .addResourceOrFileSource("application.yml", optional = true)
        .build()
        .loadConfigOrThrow()
}

fun main() {
    val app = App()
    app.registerRenderer()
    app.start()
}


fun makeDatasource(
    driverClassName: String,
    jdbcUrl: String,
    username: String?,
    password: String?,
): HikariDataSource {

    val config = HikariConfig().apply {
        this.driverClassName = driverClassName
        this.jdbcUrl = jdbcUrl
        this.username = username ?: ""
        this.password = password ?: ""
        this.maximumPoolSize = 2
    }

    return HikariDataSource(config)
}



fun connection(key: String, fetchSize: Int?, f: (Connection) -> ResultSet): Map<String, Any> {
    logger.debug { "finding datasource by key $key" }
    val dataSource = store[key]

    if (dataSource != null) {
        return dataSource.connection.use { conn ->
            val resultSet = f(conn!!)
            resultSet.use {

                val map = LinkedHashMap<String, Any>()
                map["columns"] = getMetadata(resultSet)

                if ((fetchSize ?: 0) > 0) {
                    map["data"] = getResultData(resultSet, fetchSize!!)
                }
                map
            }
        }

    } else {
        throw Exception("invalid datasource")
    }
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


fun getMetadata(resultSet: ResultSet): List<Column> {

    val columns = mutableListOf<Column>()
    val metadata = resultSet.metaData
    for (index in 1..metadata.columnCount) {

        val col = Column(
            table = metadata.getTableName(index) ?: metadata.getCatalogName(index),
            name = metadata.getColumnName(index),
            label = metadata.getColumnLabel(index),
            size = metadata.getColumnDisplaySize(index),
            nullable = metadata.isNullable(index) > 0,
            autoIncrement = metadata.isAutoIncrement(index),
            sqlType = SQLType(type = metadata.getColumnType(index), metadata.getColumnTypeName(index)),
            className = metadata.getColumnClassName(index).substringAfter("java.lang."),
            scale = Scale(metadata.getScale(index), metadata.getPrecision(index))
        )

        columns += col
    }

    return columns
}

fun getResultData(resultSet: ResultSet, fetchSize: Int): MutableList<Map<String, Any?>> {

    val metadata = resultSet.metaData
    val rows = mutableListOf<Map<String, Any?>>()
    var count = fetchSize.coerceAtMost(100)

    while (resultSet.next()) {

        if (count == 0)
            break

        val row = LinkedHashMap<String, Any?>()
        for (index in 1..metadata.columnCount) {
            row[metadata.getColumnName(index)] = resultSet.getObject(index)
        }
        rows += row

        count--

    }

    return rows
}

fun templateHandle(ctx: Context) {
//
//    val model = ctx.bodyAsClass(JavaModel::class.java)
//
//    model.template?.let {
//        ctx.render("${it.lowercase()}${FREEMARKER_EXT}",
//            mapOf("columns" to model.columns,
//                ""
//            ))
//    } ?: Exception("invalid request")
//
}

fun poolHandler(ctx: Context) {
    val db = ctx.bodyAsClass(Database::class.java)
    store[db.name!!] = makeDatasource(db.driverClassName!!, db.jdbcUrl!!, db.username, db.password)
    ctx.status(HttpStatus.OK)
}

fun connectedHandler(ctx: Context) {
    ctx.json(store.keys())
}