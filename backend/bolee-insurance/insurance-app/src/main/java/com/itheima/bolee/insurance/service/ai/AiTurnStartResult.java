package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.insurance.pojo.ai.AiTurnDO;

/** Outcome of an idempotent turn start request. */
public class AiTurnStartResult {
    public enum Outcome { CREATED, EXISTING, IDEMPOTENCY_CONFLICT, CONVERSATION_UNAVAILABLE }
    private final Outcome outcome;
    private final AiTurnDO turn;
    public AiTurnStartResult(Outcome outcome, AiTurnDO turn) { this.outcome = outcome; this.turn = turn; }
    public Outcome getOutcome() { return outcome; }
    public AiTurnDO getTurn() { return turn; }
}
