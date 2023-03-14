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
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.ContentType
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


val logger = KotlinLogging.logger { }
val store = ConcurrentHashMap<String, DataSource?>()
var config: Config = loadConfig()
var cfg: Configuration = initTemplate()




fun initTemplate(): Configuration {

    return Configuration(Configuration.VERSION_2_3_32).apply {
        cacheStorage = NullCacheStorage()

        //https://tm8r.hateblo.jp/entry/20110915/1316097034
        setSharedVariable("camel", object : TemplateMethodModelEx {
            override fun exec(arguments: MutableList<Any?>?): Any? {
                return if(arguments?.size == 1) {
                    val str = (arguments[0] as TemplateScalarModel).asString
                    CaseUtils.toCamelCase(str, false, '_')
                } else
                    null
            }
        })

        when {
              config.template.location.startsWith("classpath:") ->  ClassTemplateLoader(this::class.java.classLoader, config.template.location.substring(10))
              config.template.location.startsWith("file:") -> FileTemplateLoader(File( config.template.location.substring(5)))
              else -> throw Exception("invalid templateloader")
          }.also {
            templateLoader = it
        }
    }
}

fun registerRenderer() {

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

fun loadConfig(): Config {
    return ConfigLoaderBuilder.default()
        .addResourceSource("/application.yml", optional = true)
        .addResourceOrFileSource("application.yml", optional = true)
        .build()
        .loadConfigOrThrow()
}

fun main() {

    registerRenderer()

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
            post("/datasource", ::createDataSourceHandler)
            get("/datasource", ::getDataSourceHandler)
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
        this.maximumPoolSize = config.database.hikari?.maxPoolSize ?: 2
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

