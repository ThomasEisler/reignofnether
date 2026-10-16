package com.solegendary.reignofnether.diplomacy;

// an offer waiting for an answer. For a trade, amounts are what "from" gives (positive) or asks for (negative).
public record Proposal(Kind kind, String from, String to, int food, int wood, int ore, long time) {
    public enum Kind { PEACE, ALLIANCE, TRADE }

    public String describe() {
        return switch (kind) {
            case PEACE -> "la paix";
            case ALLIANCE -> "une alliance";
            case TRADE -> "un échange : " + DiplomacyServerEvents.describeTrade(food, wood, ore);
        };
    }
}
