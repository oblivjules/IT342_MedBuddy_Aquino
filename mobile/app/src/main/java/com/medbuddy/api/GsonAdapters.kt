package com.medbuddy.api

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * Handles backend LocalTime fields serialized as either a JSON string ("09:30:00")
 * or a JSON array ([9, 30, 0]) depending on Jackson configuration.
 * Normalizes both forms to "HH:MM".
 */
class TimeArrayOrStringAdapter : TypeAdapter<String?>() {

    override fun write(out: JsonWriter, value: String?) {
        if (value == null) out.nullValue() else out.value(value)
    }

    override fun read(reader: JsonReader): String? = when (reader.peek()) {
        JsonToken.NULL -> {
            reader.nextNull()
            null
        }
        JsonToken.STRING -> reader.nextString()
        JsonToken.BEGIN_ARRAY -> {
            reader.beginArray()
            val parts = mutableListOf<Int>()
            while (reader.hasNext()) {
                when (reader.peek()) {
                    JsonToken.NUMBER -> parts.add(reader.nextInt())
                    else -> reader.skipValue()
                }
            }
            reader.endArray()
            if (parts.size >= 2)
                "${parts[0].toString().padStart(2, '0')}:${parts[1].toString().padStart(2, '0')}"
            else null
        }
        else -> {
            reader.skipValue()
            null
        }
    }
}
