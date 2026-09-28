package com.dilaw.mohe.controler;

import com.dilaw.mohe.util.LookupNamesRequired;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

/**
 * Every row saved through the lookup API carries both names (live QA M11, D20-a): one
 * check for the 82 create and update endpoints of {@link LookupTableController}, run on
 * each request body as it is read, so no endpoint can be added without it.
 */
@ControllerAdvice(assignableTypes = LookupTableController.class)
public class LookupNamesAdvice extends RequestBodyAdviceAdapter {

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        LookupNamesRequired.require(body);
        return body;
    }
}
