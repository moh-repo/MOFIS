package com.dilaw.mohe.config;

import com.dilaw.mohe.security.field.FieldAccessService;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;

/**
 * Wraps a {@link BeanPropertyWriter} for fields annotated with {@link FieldPermission}
 * or {@link SensitiveField}. At serialize time the rule is put to
 * {@link FieldAccessService#mayView}: the catalogued {@code .VIEW} code when the
 * administrators have defined it, else the annotation's {@code anyOf}; the
 * value is written when allowed and {@code null} otherwise (the field stays
 * present so clients do not break).
 *
 * <p>Registered transparently via {@link SensitiveFieldModule} — no controller changes.
 * Before the Spring context is up (a bare {@code ObjectMapper} in a test) only
 * the {@code anyOf} fallback is applied.
 */
class SensitivePropertyWriter extends BeanPropertyWriter {

    private final BeanPropertyWriter delegate;
    private final FieldAccessService.Rule rule;

    SensitivePropertyWriter(BeanPropertyWriter base, FieldAccessService.Rule rule) {
        super(base);
        this.delegate = base;
        this.rule = rule;
    }

    @Override
    public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
        if (mayView(bean)) {
            delegate.serializeAsField(bean, gen, prov);
        } else if (!_suppressNulls) {
            gen.writeNullField(getName());
        }
        // If _suppressNulls is true (@JsonInclude NON_NULL), omit entirely for redacted fields
    }

    @Override
    public void serializeAsOmittedField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
        if (mayView(bean)) {
            delegate.serializeAsOmittedField(bean, gen, prov);
        }
    }

    @Override
    public void serializeAsElement(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
        if (mayView(bean)) {
            delegate.serializeAsElement(bean, gen, prov);
        } else {
            gen.writeNull();
        }
    }

    private boolean mayView(Object bean) {
        return FieldAccessService.current()
                .map(service -> service.mayView(rule, bean))
                .orElseGet(this::hasAnyFallbackPermission);
    }

    private boolean hasAnyFallbackPermission() {
        if (rule.anyOf().length == 0) return true;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        for (String required : rule.anyOf()) {
            for (GrantedAuthority granted : authorities) {
                if (required.equals(granted.getAuthority())) return true;
            }
        }
        return false;
    }
}
