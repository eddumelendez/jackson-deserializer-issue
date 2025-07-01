package org.example;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonJsonAnyGetterTest {
    
    @Test
    void test() throws JsonProcessingException {
        Child child = new Child();
        child.setProperty1("value1");
        Accessor.overrideRawValue(child, "Property2", "value2");

        ObjectMapper objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        ObjectMapper originalObjectMapper = objectMapper.copy();
        objectMapper.registerModule(new SimpleModule("docker-java") {
            @Override
            public void setupModule(SetupContext context) {
                super.setupModule(context);
                context.addBeanDeserializerModifier(new BeanDeserializerModifier() {
                    @Override
                    public JsonDeserializer<?> modifyDeserializer(
                            DeserializationConfig config,
                            BeanDescription beanDescription,
                            JsonDeserializer<?> originalDeserializer
                    ) {
                        if (!beanDescription.getType().isTypeOrSubTypeOf(Parent.class)) {
                            return originalDeserializer;
                        }

                        return new DockerObjectDeserializer(
                                originalDeserializer,
                                beanDescription,
                                originalObjectMapper
                        );
                    }
                });
            }
        });
        
        String json = objectMapper.writeValueAsString(child);
        Child child1 = objectMapper.readValue(json, Child.class);

        assertThat(child1.getProperty2()).isEqualTo("value2");
    }

    static abstract class Parent {

        HashMap<String, Object> rawValues = new HashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getRawValues() {
            return Collections.unmodifiableMap(this.rawValues);
        }
        
    }
    
    static class Child extends Parent implements Serializable {
        
        @JsonProperty("Property1")
        private String property1;

        @JsonProperty("Property2")
        private String property2;

        public String getProperty1() {
            return property1;
        }

        public void setProperty1(String property1) {
            this.property1 = property1;
        }

        public String getProperty2() {
            return property2;
        }

        public void setProperty2(String property2) {
            this.property2 = property2;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Child child = (Child) o;
            return Objects.equals(property1, child.property1) && Objects.equals(property2, child.property2);
        }

        @Override
        public int hashCode() {
            return Objects.hash(property1, property2);
        }
    }

    class DockerObjectDeserializer extends DelegatingDeserializer {

        private final BeanDescription beanDescription;

        private final ObjectMapper originalMapper;

        DockerObjectDeserializer(
                JsonDeserializer<?> delegate,
                BeanDescription beanDescription,
                ObjectMapper originalMapper
        ) {
            super(delegate);
            this.beanDescription = beanDescription;
            this.originalMapper = originalMapper;
        }

        @Override
        protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegatee) {
            return new DockerObjectDeserializer(newDelegatee, beanDescription, originalMapper);
        }

        @Override
        @SuppressWarnings({"deprecation", "unchecked"})
        public Object deserialize(JsonParser p, DeserializationContext ctxtx) throws IOException {
            JsonNode jsonNode = p.readValueAsTree();

            Object deserializedObject = originalMapper.treeToValue(jsonNode, beanDescription.getBeanClass());

            if (deserializedObject instanceof Parent) {
                Accessor.overrideRawValues(
                        ((Parent) deserializedObject),
                        originalMapper.convertValue(jsonNode, HashMap.class)
                );
            }

            return deserializedObject;
        }
        
    }
    
    static class Accessor {

        static void overrideRawValues(Parent o, HashMap<String, Object> rawValues) {
            o.rawValues = rawValues != null ? rawValues : new HashMap<>();
        }

        public static void overrideRawValue(Parent o, String key, Object value) {
            o.rawValues.put(key, value);
        }
        
    }
    
}
