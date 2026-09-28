package com.dilaw.mohe.audit;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the {@link AuditedRead} row after a handler returns (O-33).
 *
 * <p>After, not before: a refused read is already an {@code ACCESS_DENIED} row
 * (O-32), and a read that failed handed nothing out. The record is described by
 * the handler's path variables and request parameters, by name, so the row says
 * {@code docId=42} rather than an argument position. Never throws — the audit
 * must not turn a served file into a 500 — and the insert is asynchronous.
 */
@Aspect
@Component
public class AuditedReadAspect {

    private final ReportAccessLogService reportAccessLog;
    private final boolean detailViews;

    public AuditedReadAspect(ReportAccessLogService reportAccessLog,
                             @Value("${mohe.audit.detail-views:false}") boolean detailViews) {
        this.reportAccessLog = reportAccessLog;
        this.detailViews = detailViews;
    }

    @AfterReturning("@annotation(audited)")
    public void record(JoinPoint joinPoint, AuditedRead audited) {
        if (audited.detailView() && !detailViews) return;
        try {
            reportAccessLog.logExport(audited.resource(), audited.format(), describe(joinPoint));
        } catch (RuntimeException ignored) {
            // logExport already swallows; belt and braces for the aspect itself.
        }
    }

    /** {@code docId=42,lang=ar}: the handler's named arguments, scalars only, capped for the column. */
    static String describe(JoinPoint joinPoint) {
        List<String> parts = new ArrayList<>();
        if (joinPoint.getSignature() instanceof MethodSignature sig) {
            Parameter[] params = sig.getMethod().getParameters();
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < params.length && i < args.length; i++) {
                Object v = args[i];
                if (v == null) continue;
                if (v instanceof Number || v instanceof CharSequence || v instanceof Boolean || v instanceof Enum<?>) {
                    parts.add(params[i].getName() + "=" + v);
                }
            }
        }
        String summary = String.join(",", parts);
        return summary.length() > 1000 ? summary.substring(0, 1000) : summary;
    }
}
