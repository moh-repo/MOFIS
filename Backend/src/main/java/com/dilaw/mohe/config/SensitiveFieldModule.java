package com.dilaw.mohe.config;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Jackson {@link SimpleModule} that replaces {@link BeanPropertyWriter} instances for
 * fields annotated with {@link SensitiveField} with a {@link SensitivePropertyWriter}
 * that gates serialization against the current Spring Security context.
 *
 * <p>Register as a {@code @Bean} so Spring Boot's auto-configured {@code ObjectMapper}
 * picks it up automatically.
 */
public class SensitiveFieldModule extends SimpleModule {

    public SensitiveFieldModule() {
        super("SensitiveFieldModule");
        setSerializerModifier(new SensitiveFieldBeanSerializerModifier());
    }

    private static class SensitiveFieldBeanSerializerModifier extends BeanSerializerModifier {

        @Override
        public List<BeanPropertyWriter> changeProperties(
                SerializationConfig config,
                BeanDescription beanDesc,
                List<BeanPropertyWriter> beanProperties) {

            return beanProperties.stream()
                    .map(writer -> {
                        AnnotatedMember member = writer.getMember();
                        if (member instanceof AnnotatedField) {
                            // R-18: @FieldPermission is the general form; @SensitiveField the read-only fallback.
                            FieldPermission fp = member.getAnnotation(FieldPermission.class);
                            if (fp != null) {
                                return new SensitivePropertyWriter(writer,
                                        com.dilaw.mohe.security.field.FieldAccessService.Rule.of(fp));
                            }
                            SensitiveField annotation = member.getAnnotation(SensitiveField.class);
                            if (annotation != null && annotation.anyOf().length > 0) {
                                return new SensitivePropertyWriter(writer,
                                        com.dilaw.mohe.security.field.FieldAccessService.Rule.of(annotation));
                            }
                        }
                        return writer;
                    })
                    .collect(Collectors.toList());
        }
    }
}
