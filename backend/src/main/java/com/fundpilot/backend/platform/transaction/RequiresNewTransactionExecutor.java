package com.fundpilot.backend.platform.transaction;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/** 在独立事务中执行单个批处理单元，确保失败只回滚当前单元。 */
@Component
public class RequiresNewTransactionExecutor {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T execute(Supplier<T> work) {
        return work.get();
    }
}
