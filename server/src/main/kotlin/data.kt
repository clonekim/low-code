package scratch

const val FREEMARKER_EXT = ".ftl"

data class Server(val port: Int, val debug: Boolean = false, val showBanner: Boolean = false)
data class Database(
    var name: String? = null,
    var jdbcUrl: String? = null,
    var driverClassName: String? = null,
    var username: String? = null,
    var password: String? = null,
    var hikari: Hikari? = null
)

data class Hikari(var maxPoolSize: Int?)

data class Template(val location: String)
data class Config(val server: Server, val database: Database, val template: Template)

data class Query(
    var statement: String? = null,
    var schema: String? = null,
    var table: String? = null,
    var fetchSize: Int? = null
)


open class Column(
    var table: String? = null,
    var name: String? = null,
    var label: String?= null,
    val size: Int? = null,
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
    val property: String? = null,
    val column: Column
)

class JavaClass  (
    val packageName: String,
    val validation: Boolean? = false,
    val builder: Boolean? = false,
    val template: String,
    val properties: List<JavaModel> = mutableListOf()
): Column()

