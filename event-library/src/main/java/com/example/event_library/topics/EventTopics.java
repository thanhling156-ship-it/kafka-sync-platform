package com.example.event_library.topics;

public final class EventTopics {

    // Order
    public static final String ORDER_CREATED = "order-created";

    // Inventory
    public static final String REPO_SUCCESS = "repo-success";
    public static final String REPO_FAIL = "repo-fail";

    // Payment
    public static final String PAY_SUCCESS = "pay-success";
    public static final String PAY_FAIL = "pay-fail";

    // Shipping
    public static final String SHIP_SUCCESS = "ship-success";
    public static final String SHIP_FAIL = "ship-fail";

    private EventTopics() {} // prevent instantiation
}