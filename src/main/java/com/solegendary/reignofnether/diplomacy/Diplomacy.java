package com.solegendary.reignofnether.diplomacy;

// checks usable on both sides (unit actions run on the client too)
public class Diplomacy {
    public static boolean atPeace(boolean clientSide, String a, String b) {
        return clientSide ? DiplomacyClient.atPeace(a, b) : DiplomacyServerEvents.atPeace(a, b);
    }
}
