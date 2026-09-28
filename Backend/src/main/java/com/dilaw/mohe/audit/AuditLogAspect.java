package com.dilaw.mohe.audit;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** Disabled by default: audit is written by DB triggers to AUDITLOG.AuditTrail. */
@Aspect
@Component
@ConditionalOnProperty(name = "app.audit.aspect.enabled", havingValue = "true")
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    @Autowired
    private AuditLoggingService auditLoggingService;

    private final ThreadLocal<Map<String, Object>> oldEntityCache = new ThreadLocal<>();

    // Intercept repository save operations
    // Using target() to intercept the actual implementation, not just the interface
    @Before("execution(* com.dilaw.mohe.repository.*.save(..)) || execution(* org.springframework.data.repository.CrudRepository+.save(..))")
    public void beforeSave(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] != null) {
            Object entity = args[0];
            // Skip audit log entities
            if (entity instanceof MoheAuditLog) {
                return;
            }
            
            try {
                log.debug("=== AUDIT LOG ASPECT: beforeSave called for  {}", entity.getClass().getSimpleName() + " ===");
                
                // Check if entity has an ID (update) or not (insert)
                String recordId = getRecordId(entity);
                log.debug("Before save - Record ID:  {}", recordId);
                
                if (recordId != null) {
                    // This is an update - fetch the old entity from repository
                    log.debug("Fetching old entity for update...");
                    Object oldEntity = fetchOldEntity(joinPoint.getTarget(), entity, recordId);
                    log.debug("Old entity fetched:  {}", (oldEntity != null ? "Yes" : "No"));
                    
                    Map<String, Object> cache = oldEntityCache.get();
                    if (cache == null) {
                        cache = new HashMap<>();
                        oldEntityCache.set(cache);
                    }
                    cache.put(joinPoint.getSignature().toLongString(), oldEntity);
                } else {
                    log.debug("No ID found - this is likely an INSERT operation");
                }
            } catch (Exception e) {
                log.error("ERROR in beforeSave:  {}", e.getMessage());
            }
        }
    }

    @AfterReturning(pointcut = "execution(* com.dilaw.mohe.repository.*.save(..)) || execution(* org.springframework.data.repository.CrudRepository+.save(..))", returning = "result")
    public void afterSave(JoinPoint joinPoint, Object result) {
        if (result == null || result instanceof MoheAuditLog) {
            return;
        }
        
        try {
            log.debug("=== AUDIT LOG ASPECT: afterSave called for  {}", result.getClass().getSimpleName() + " ===");
            
            String recordId = getRecordId(result);
            log.debug("Record ID:  {}", recordId);
            
            if (recordId != null) {
                // This is an update operation (entity has ID)
                Map<String, Object> cache = oldEntityCache.get();
                Object oldEntity = cache != null ? cache.get(joinPoint.getSignature().toLongString()) : null;
                
                log.debug("Logging UPDATE operation for record ID:  {}", recordId);
                // Log the update
                auditLoggingService.logUpdate(result, oldEntity);
                log.debug("UPDATE logged successfully");
                
                // Clean up cache
                if (cache != null) {
                    cache.remove(joinPoint.getSignature().toLongString());
                    if (cache.isEmpty()) {
                        oldEntityCache.remove();
                    }
                }
            } else {
                // This might be an insert, but we can't log it without an ID
                // The ID is generated after save, so we check again
                log.debug("No record ID found - might be an insert, skipping audit log");
            }
        } catch (Exception e) {
            log.error("ERROR in audit logging aspect for save:  {}", e.getMessage());
        }
    }

    // Intercept repository deleteById operations
    @Before("execution(* com.dilaw.mohe.repository.*.deleteById(..)) || execution(* org.springframework.data.repository.CrudRepository+.deleteById(..))")
    public void beforeDeleteById(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] != null) {
            try {
                // Fetch the entity before deletion
                Object repository = joinPoint.getTarget();
                if (repository instanceof CrudRepository) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    CrudRepository crudRepo = (CrudRepository) repository;
                    @SuppressWarnings("null")
                    Object entity = crudRepo.findById(args[0]).orElse(null);
                    if (entity != null && !(entity instanceof MoheAuditLog)) {
                        Map<String, Object> cache = oldEntityCache.get();
                        if (cache == null) {
                            cache = new HashMap<>();
                            oldEntityCache.set(cache);
                        }
                        cache.put(joinPoint.getSignature().toLongString(), entity);
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @AfterReturning(pointcut = "execution(* com.dilaw.mohe.repository.*.deleteById(..)) || execution(* org.springframework.data.repository.CrudRepository+.deleteById(..))")
    public void afterDeleteById(JoinPoint joinPoint) {
        Map<String, Object> cache = oldEntityCache.get();
        if (cache != null) {
            Object entity = cache.get(joinPoint.getSignature().toLongString());
            if (entity != null && !(entity instanceof MoheAuditLog)) {
                try {
                    auditLoggingService.logDelete(entity);
                } catch (Exception e) {
                    log.error("Error in audit logging aspect for deleteById:  {}", e.getMessage());
                }
                cache.remove(joinPoint.getSignature().toLongString());
                if (cache.isEmpty()) {
                    oldEntityCache.remove();
                }
            }
        }
    }

    // Intercept repository delete operations (with entity)
    @Before("execution(* com.dilaw.mohe.repository.*.delete(..)) || execution(* org.springframework.data.repository.CrudRepository+.delete(..))")
    public void beforeDelete(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] != null) {
            Object entity = args[0];
            if (entity instanceof MoheAuditLog) {
                return;
            }
            // Cache the entity for logging
            Map<String, Object> cache = oldEntityCache.get();
            if (cache == null) {
                cache = new HashMap<>();
                oldEntityCache.set(cache);
            }
            cache.put(joinPoint.getSignature().toLongString(), entity);
        }
    }

    @AfterReturning(pointcut = "execution(* com.dilaw.mohe.repository.*.delete(..)) || execution(* org.springframework.data.repository.CrudRepository+.delete(..))")
    public void afterDelete(JoinPoint joinPoint) {
        Map<String, Object> cache = oldEntityCache.get();
        if (cache != null) {
            Object entity = cache.get(joinPoint.getSignature().toLongString());
            if (entity != null && !(entity instanceof MoheAuditLog)) {
                try {
                    auditLoggingService.logDelete(entity);
                } catch (Exception e) {
                    log.error("Error in audit logging aspect for delete:  {}", e.getMessage());
                }
                cache.remove(joinPoint.getSignature().toLongString());
                if (cache.isEmpty()) {
                    oldEntityCache.remove();
                }
            }
        }
    }

    private Object fetchOldEntity(Object repository, Object entity, String recordId) {
        try {
            if (repository instanceof CrudRepository) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                CrudRepository crudRepo = (CrudRepository) repository;
                
                // Try to parse the ID
                Object id = parseId(recordId, entity);
                if (id != null) {
                    @SuppressWarnings("null")
                    Object oldEntity = crudRepo.findById(id).orElse(null);
                    return oldEntity;
                }
            }
        } catch (Exception e) {
            // Ignore - return null if we can't fetch
        }
        return null;
    }

    private Object parseId(String recordId, Object entity) {
        try {
            java.lang.reflect.Field[] fields = entity.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if (field.isAnnotationPresent(jakarta.persistence.Id.class)) {
                    Class<?> idType = field.getType();
                    if (idType == Integer.class || idType == int.class) {
                        return Integer.parseInt(recordId);
                    } else if (idType == Long.class || idType == long.class) {
                        return Long.parseLong(recordId);
                    } else if (idType == String.class) {
                        return recordId;
                    }
                    // Try to parse as the field type
                    return recordId;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return recordId;
    }

    private String getRecordId(Object entity) {
        try {
            java.lang.reflect.Field[] fields = entity.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if (field.isAnnotationPresent(jakarta.persistence.Id.class)) {
                    field.setAccessible(true);
                    Object id = field.get(entity);
                    return id != null ? String.valueOf(id) : null;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}
