package com.rental.chat.event;

import java.util.Collection;

public record ChatEvent(Collection<Long> userIds, String event, Object payload) {
}
