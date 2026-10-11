package smoke

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@Serializable
data class Payload(val message: String)

@Serializable
data class NamedPayload(val number: Int) { companion object Factory }

@Serializable
object SingletonPayload

object SerializationSmoke {
    @JvmStatic fun main(args: Array<String>) = run()
    @JvmStatic
    fun run() {
        // Java Type lookup exercises reflection rather than compiler-inlined serializer access.
        @Suppress("UNCHECKED_CAST")
        val codec = serializer(Payload::class.java) as KSerializer<Payload>
        check(Json.decodeFromString(codec, """{"message":"hello"}""").message == "hello")
        @Suppress("UNCHECKED_CAST")
        val namedCodec = serializer(NamedPayload::class.java) as KSerializer<NamedPayload>
        check(Json.decodeFromString(namedCodec, """{"number":42}""").number == 42)
        check(serializer(SingletonPayload::class.java).descriptor.elementsCount == 0)
        println("PASS serialization")
    }
}
