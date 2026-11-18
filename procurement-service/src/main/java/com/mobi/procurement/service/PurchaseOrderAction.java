package com.mobi.procurement.service;

public enum PurchaseOrderAction {
    EDIT("edit"),
    SEND("send"),
    CANCEL("cancel"),
    ACCEPT("accept"),
    REJECT("reject"),
    SHIP("send a shipment notice for"),
    RECEIVE("receive goods against"),
    CLOSE("close");

    private final String verb;

    PurchaseOrderAction(String verb) {
        this.verb = verb;
    }

    public String verb() {
        return verb;
    }
}
