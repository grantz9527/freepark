package com.freepark.local.accessdecision;

/** Result of an access decision for a lane event. */
public record AccessDecisionView(Result result, String remark) {

  public enum Result {
    ALLOWED,
    INTERCEPTED
  }

  public static AccessDecisionView allowed(String remark) {
    return new AccessDecisionView(Result.ALLOWED, remark);
  }

  public static AccessDecisionView intercepted(String remark) {
    return new AccessDecisionView(Result.INTERCEPTED, remark);
  }
}
