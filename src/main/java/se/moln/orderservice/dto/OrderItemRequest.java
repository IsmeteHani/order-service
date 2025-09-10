package se.moln.orderservice.dto;

import java.util.UUID;

public record OrderItemRequest(
        UUID productId,
        int quantity
) {}