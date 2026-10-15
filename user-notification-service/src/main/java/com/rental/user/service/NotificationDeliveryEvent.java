package com.rental.user.service;

import java.util.List;

public record NotificationDeliveryEvent(String title, String content, String email,
        List<String> deviceTokens) { }
