package dev.reactfuscator.model;

public final class LeakFinding {
    public final String category,entry,symbol,location,status,reason;
    public long occurrences;
    public LeakFinding(String category,String entry,String symbol,String location,String status,String reason){this.category=category;this.entry=entry;this.symbol=symbol;this.location=location;this.status=status;this.reason=reason;this.occurrences=1;}
}
