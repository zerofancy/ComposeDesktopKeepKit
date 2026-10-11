package isolated

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@OptIn(ExperimentalSerializationApi::class)
private object PrimitiveFormat : StringFormat {
    override val serializersModule = EmptySerializersModule()
    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        var encoded = ""
        serializer.serialize(object : AbstractEncoder() {
            override val serializersModule = PrimitiveFormat.serializersModule
            override fun encodeString(value: String) { encoded = value }
        }, value)
        return encoded
    }
    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T =
        deserializer.deserialize(object : AbstractDecoder() {
            override val serializersModule = PrimitiveFormat.serializersModule
            override fun decodeString() = string
            override fun decodeElementIndex(descriptor: SerialDescriptor) = CompositeDecoder.DECODE_DONE
        })
}

object ConverterSmoke {
    @JvmStatic fun main(args: Array<String>) {
        // The converter ships core, not JSON. Supply an application format using core alone.
        val mediaType = "text/plain".toMediaType()
        val retrofit = Retrofit.Builder().baseUrl("http://127.0.0.1/")
            .addConverterFactory(PrimitiveFormat.asConverterFactory(mediaType)).build()
        val response = retrofit.responseBodyConverter<String>(String::class.java, emptyArray())
        check(response.convert("hello".toResponseBody(mediaType)) == "hello")
        val request = retrofit.requestBodyConverter<String>(String::class.java, emptyArray(), emptyArray())
        val output = Buffer()
        request.convert("hello")!!.writeTo(output)
        check(output.readUtf8() == "hello")
        println("PASS retrofit-converter isolated")
    }
}
