package com.fundpilot.backend.discipline.adapter.api.adviceresponse;

import com.fundpilot.backend.discipline.application.command.adviceresponse.AdviceResponseCommandHandler;
import com.fundpilot.backend.discipline.application.command.adviceresponse.AdviceResponseFailure;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Discipline 对建议接受和忽略的公开入站契约。 */
@Component
@RequiredArgsConstructor
public class AdviceResponseApi {
    private final AdviceResponseCommandHandler commands;

    public AcceptedAdvice accept(AcceptAdvice request) {
        try {
            var result = commands.accept(request.ownerId(), request.adviceId(), request.amount(), request.shares(),
                    request.tradeDate());
            return new AcceptedAdvice(result.adviceId(), result.transactionId());
        } catch (AdviceResponseFailure failure) {
            throw new Failure(Code.valueOf(failure.code().name()), failure.getMessage());
        }
    }

    public void ignore(IgnoreAdvice request) {
        try {
            commands.ignore(request.ownerId(), request.adviceId());
        } catch (AdviceResponseFailure failure) {
            throw new Failure(Code.valueOf(failure.code().name()), failure.getMessage());
        }
    }

    public record AcceptAdvice(long ownerId, long adviceId, BigDecimal amount, BigDecimal shares,
                               Instant tradeDate) {
    }

    public record IgnoreAdvice(long ownerId, long adviceId) {
    }

    public record AcceptedAdvice(long adviceId, long transactionId) {
    }

    public static final class Failure extends RuntimeException {
        private final Code code;

        private Failure(Code code, String message) {
            super(message);
            this.code = code;
        }

        public Code code() { return code; }
    }

    public enum Code { ADVICE_NOT_FOUND, ADVICE_IGNORED, ADVICE_NOT_ACTIONABLE, VALUE_REQUIRED, ALREADY_RESPONDED }
}
