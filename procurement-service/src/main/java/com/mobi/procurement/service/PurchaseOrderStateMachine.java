package com.mobi.procurement.service;

import com.mobi.procurement.entity.PurchaseOrder;
import com.mobi.procurement.entity.PurchaseOrderStatus;
import com.mobi.procurement.exception.ConflictException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.mobi.procurement.entity.PurchaseOrderStatus.ACCEPTED;
import static com.mobi.procurement.entity.PurchaseOrderStatus.CANCELLED;
import static com.mobi.procurement.entity.PurchaseOrderStatus.DRAFT;
import static com.mobi.procurement.entity.PurchaseOrderStatus.PARTIALLY_RECEIVED;
import static com.mobi.procurement.entity.PurchaseOrderStatus.RECEIVED;
import static com.mobi.procurement.entity.PurchaseOrderStatus.REJECTED;
import static com.mobi.procurement.entity.PurchaseOrderStatus.SENT;
import static com.mobi.procurement.entity.PurchaseOrderStatus.SHIPPED;

/**
 * The one place that decides which actions a purchase order allows and where each
 * leads (docs/specs/procurement.md §6.2).
 *
 * <pre>
 * DRAFT ─send─► SENT ─accept─► ACCEPTED ─ship─► SHIPPED ─receive─► PARTIALLY_RECEIVED ─receive─► RECEIVED
 *                 └─reject─► REJECTED                                     └──────close short──────►┘
 * cancel: from DRAFT, SENT, ACCEPTED or SHIPPED, i.e. never once goods have been counted in.
 * </pre>
 */
public final class PurchaseOrderStateMachine {

    private static final Map<PurchaseOrderAction, Set<PurchaseOrderStatus>> ALLOWED_FROM = new EnumMap<>(PurchaseOrderAction.class);

    static {
        ALLOWED_FROM.put(PurchaseOrderAction.EDIT, EnumSet.of(DRAFT));
        ALLOWED_FROM.put(PurchaseOrderAction.SEND, EnumSet.of(DRAFT));
        ALLOWED_FROM.put(PurchaseOrderAction.CANCEL, EnumSet.of(DRAFT, SENT, ACCEPTED, SHIPPED));
        ALLOWED_FROM.put(PurchaseOrderAction.ACCEPT, EnumSet.of(SENT));
        ALLOWED_FROM.put(PurchaseOrderAction.REJECT, EnumSet.of(SENT));
        ALLOWED_FROM.put(PurchaseOrderAction.SHIP, EnumSet.of(ACCEPTED, SHIPPED, PARTIALLY_RECEIVED));
        // Goods can turn up before (or without) a shipment notice.
        ALLOWED_FROM.put(PurchaseOrderAction.RECEIVE, EnumSet.of(ACCEPTED, SHIPPED, PARTIALLY_RECEIVED));
        ALLOWED_FROM.put(PurchaseOrderAction.CLOSE, EnumSet.of(PARTIALLY_RECEIVED));
    }

    private PurchaseOrderStateMachine() {
    }

    public static boolean allows(PurchaseOrderStatus status, PurchaseOrderAction action) {
        return ALLOWED_FROM.get(action).contains(status);
    }

    /** Throws 409 unless {@code action} is allowed in the PO's current status. */
    public static void check(PurchaseOrder po, PurchaseOrderAction action) {
        if (!allows(po.getStatus(), action)) {
            throw new ConflictException("Cannot %s purchase order %s while it is %s"
                    .formatted(action.verb(), po.getPoNumber(), po.getStatus()));
        }
    }

    /**
     * The status after {@code action}, given the PO as it stands once the action's own
     * changes (shipped or received quantities) have been applied.
     */
    public static PurchaseOrderStatus next(PurchaseOrder po, PurchaseOrderAction action) {
        check(po, action);
        PurchaseOrderStatus current = po.getStatus();
        return switch (action) {
            case EDIT -> current;
            case SEND -> SENT;
            case CANCEL -> CANCELLED;
            case ACCEPT -> ACCEPTED;
            case REJECT -> REJECTED;
            // The first notice marks the PO shipped; later ones (including replacements
            // after a short receipt) leave the status alone.
            case SHIP -> current == ACCEPTED ? SHIPPED : current;
            case RECEIVE -> po.fullyAccountedFor() ? RECEIVED : PARTIALLY_RECEIVED;
            case CLOSE -> RECEIVED;
        };
    }

    /** Checks the action and moves the PO to its next status. */
    public static void apply(PurchaseOrder po, PurchaseOrderAction action, String note) {
        PurchaseOrderStatus next = next(po, action);
        if (next != po.getStatus() || note != null) {
            po.moveTo(next, note);
        }
    }

    /** Actions open to anyone on the PO in its current status; the UI shows buttons from this. */
    public static Set<PurchaseOrderAction> available(PurchaseOrderStatus status) {
        Set<PurchaseOrderAction> actions = EnumSet.noneOf(PurchaseOrderAction.class);
        ALLOWED_FROM.forEach((action, from) -> {
            if (from.contains(status)) {
                actions.add(action);
            }
        });
        return actions;
    }
}
