package com.fundpilot.backend.exception;

/**
 * 资源未找到异常,code 固定 {@code ENTITY_NOT_FOUND},{@code message} 形如
 * {@code "Fund #1 不存在"}。作为 {@link BusinessException} 子类统一映射 HTTP 400。
 *
 * @param entityName 实体名(如 {@code "Fund"})
 * @param id 找不到的主键
 */
public class EntityNotFoundException extends BusinessException {

    public EntityNotFoundException(String entityName, Object id) {
        super("ENTITY_NOT_FOUND", entityName + " #" + id + " 不存在");
    }
}
