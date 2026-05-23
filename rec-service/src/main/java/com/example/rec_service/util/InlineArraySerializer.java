package com.example.rec_service.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.List;

public class InlineArraySerializer extends JsonSerializer<List<Integer>> {
    @Override
    public void serialize(List<Integer> value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        // Ép Jackson viết giá trị mảng dưới dạng chuỗi thô [1, 2, 3]
        // Thay vì dùng writeStartArray() thông thường
        gen.writeRawValue(value.toString());
    }
}