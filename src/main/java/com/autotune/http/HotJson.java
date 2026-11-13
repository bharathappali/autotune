package com.autotune.http;

import com.autotune.analyzer.utils.AnalyzerConstants;
import com.autotune.common.data.metrics.MetricAggregationInfoResults;
import com.autotune.utils.KruizeConstants;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * One mapper for the hot path. Field names follow {@link SerializedName} so the JSON
 * matches the Gson adapters the functional suite already parses.
 */
public final class HotJson {
    public static final DateTimeFormatter UTC_TIMESTAMP = DateTimeFormatter
            .ofPattern(KruizeConstants.DateFormats.STANDARD_JSON_DATE_FORMAT)
            .withZone(ZoneOffset.UTC);

    public static final byte[] UPDATE_RESULTS_SUCCESS = (
            "{\"message\":\"Results added successfully! View saved results at /listExperiments.\","
                    + "\"httpcode\":201,\"documentationLink\":\"\",\"status\":\"SUCCESS\"}"
    ).getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private static final ObjectMapper MAPPER = build();
    public static final ObjectReader TREE_READER = MAPPER.reader();
    public static final ObjectWriter COMPACT_WRITER = MAPPER.writer();

    private HotJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String formatTimestamp(long epochMillis) {
        return UTC_TIMESTAMP.format(Instant.ofEpochMilli(epochMillis));
    }

    private static ObjectMapper build() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setAnnotationIntrospector(AnnotationIntrospector.pair(
                new SerializedNameIntrospector(),
                new JacksonAnnotationIntrospector()));
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        SimpleModule module = new SimpleModule("kruize-hot");
        module.addSerializer(Date.class, new UtcDateSerializer());
        module.addSerializer(Timestamp.class, new UtcTimestampSerializer());
        module.addKeySerializer(Timestamp.class, new UtcTimestampKeySerializer());
        module.addKeySerializer(Date.class, new UtcDateKeySerializer());
        module.addSerializer(AnalyzerConstants.RecommendationItem.class, new RecommendationItemSerializer());
        module.addKeySerializer(AnalyzerConstants.RecommendationItem.class, new RecommendationItemKeySerializer());
        module.addSerializer(MetricAggregationInfoResults.class, new AggregationIntSerializer());
        module.setSerializerModifier(new SkipResultsModifier());
        mapper.registerModule(module);
        mapper.registerModule(new BlackbirdModule());
        return mapper;
    }

    static final class SerializedNameIntrospector extends JacksonAnnotationIntrospector {
        @Override
        public com.fasterxml.jackson.databind.PropertyName findNameForSerialization(Annotated a) {
            SerializedName name = a.getAnnotation(SerializedName.class);
            if (name != null) {
                return com.fasterxml.jackson.databind.PropertyName.construct(name.value());
            }
            return super.findNameForSerialization(a);
        }

        @Override
        public com.fasterxml.jackson.databind.PropertyName findNameForDeserialization(Annotated a) {
            SerializedName name = a.getAnnotation(SerializedName.class);
            if (name != null) {
                return com.fasterxml.jackson.databind.PropertyName.construct(name.value());
            }
            return super.findNameForDeserialization(a);
        }
    }

    /**
     * The recommendation response must not emit the raw results series or the metrics list.
     */
    static final class SkipResultsModifier extends BeanSerializerModifier {
        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
            Class<?> raw = beanDesc.getBeanClass();
            String name = raw.getName();
            if (name.equals("com.autotune.common.data.result.ContainerData")
                    || name.equals("com.autotune.analyzer.serviceObjects.ContainerAPIObject")) {
                beanProperties.removeIf(writer -> {
                    String field = writer.getName();
                    return "results".equals(field) || "metrics".equals(field);
                });
            }
            return beanProperties;
        }
    }

    static final class UtcDateSerializer extends JsonSerializer<Date> {
        @Override
        public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(formatTimestamp(value.getTime()));
        }
    }

    static final class UtcTimestampSerializer extends JsonSerializer<Timestamp> {
        @Override
        public void serialize(Timestamp value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(formatTimestamp(value.getTime()));
        }
    }

    static final class UtcTimestampKeySerializer extends JsonSerializer<Timestamp> {
        @Override
        public void serialize(Timestamp value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeFieldName(formatTimestamp(value.getTime()));
        }
    }

    static final class UtcDateKeySerializer extends JsonSerializer<Date> {
        @Override
        public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeFieldName(formatTimestamp(value.getTime()));
        }
    }

    static final class RecommendationItemSerializer extends JsonSerializer<AnalyzerConstants.RecommendationItem> {
        @Override
        public void serialize(AnalyzerConstants.RecommendationItem value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.toString());
        }
    }

    static final class RecommendationItemKeySerializer extends JsonSerializer<AnalyzerConstants.RecommendationItem> {
        @Override
        public void serialize(AnalyzerConstants.RecommendationItem value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeFieldName(value.toString());
        }
    }

    /**
     * avg, sum, min, max, median, mode and range are truncated toward zero. count stays an integer.
     */
    static final class AggregationIntSerializer extends JsonSerializer<MetricAggregationInfoResults> {
        @Override
        public void serialize(MetricAggregationInfoResults value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            writeInt(gen, "avg", value.getAvg());
            writeInt(gen, "sum", value.getSum());
            writeInt(gen, "min", value.getMin());
            writeInt(gen, "max", value.getMax());
            writeInt(gen, "median", value.getMedian());
            writeInt(gen, "mode", value.getMode());
            writeInt(gen, "range", value.getRange());
            if (value.getCount() != null) {
                gen.writeNumberField("count", value.getCount());
            }
            if (value.getFormat() != null && !value.getFormat().isEmpty()) {
                gen.writeStringField("format", value.getFormat());
            }
            gen.writeEndObject();
        }

        private static void writeInt(JsonGenerator gen, String field, Double number) throws IOException {
            if (number != null) {
                gen.writeNumberField(field, number.intValue());
            }
        }
    }
}
