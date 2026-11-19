package com.itheima.bolee.insurance.web.ai.internal;

import com.itheima.bolee.framework.commons.basic.ResponseResult;
import com.itheima.bolee.framework.commons.enums.basic.BaseEnum;
import com.itheima.bolee.framework.commons.utils.ResponseResultBuild;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Preserves contract HTTP status codes for private Agent tool validation and authorization failures. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = AiProductCatalogController.class)
public class AgentToolExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ResponseResult<Object>> handleResponseStatusException(ResponseStatusException exception) {
        ResponseResult<Object> body = ResponseResultBuild.build(
                BaseEnum.VALID_EXCEPTION,
                exception.getReason() == null ? "Agent tool request failed" : exception.getReason());
        return ResponseEntity.status(exception.getStatus()).body(body);
    }
}
