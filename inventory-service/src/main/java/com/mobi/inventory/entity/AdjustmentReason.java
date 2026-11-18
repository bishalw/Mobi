package com.mobi.inventory.entity;

/** Why stock changed. The sign each reason allows is enforced by {@link #allows(int)}. */
public enum AdjustmentReason {
    /** Opening stock for a product, e.g. when it is first stocked or seeded. */
    INITIAL,
    /** Goods counted in against a purchase order. Only procurement events use this. */
    RECEIVED,
    /** Written off as broken or unsellable. */
    DAMAGED,
    /** Missing: theft, misplacement. */
    LOST,
    /** A stock count found a different number than the system. */
    COUNT_CORRECTION;

    public boolean allows(int delta) {
        return switch (this) {
            case INITIAL, RECEIVED -> delta > 0;
            case DAMAGED, LOST -> delta < 0;
            case COUNT_CORRECTION -> delta != 0;
        };
    }

    public boolean requiresNote() {
        return this == DAMAGED || this == LOST || this == COUNT_CORRECTION;
    }
}
