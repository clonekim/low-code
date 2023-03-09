
class User(val id: Int, val name: String)


fun main() {
    val users = arrayOf(
        User(1, "Kim"),
        User(2, "Park"),
        User(3, "Lee"),
        User(4, "Tanaka")
    )

    val m1 = users.associate {  it.id to it }
    val m2 = users.associateBy { User::id }
    val m3 = users.associate { it.id to mapOf(it.id to it.name) }


    println(m1)
    println(m2)
    println(m3)


}