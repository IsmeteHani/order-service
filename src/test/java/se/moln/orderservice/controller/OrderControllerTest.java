package se.moln.orderservice.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import se.moln.orderservice.dto.OrderHistoryDto;
import se.moln.orderservice.dto.PurchaseRequest;
import se.moln.orderservice.dto.PurchaseResponse;
import se.moln.orderservice.model.OrderStatus;
import se.moln.orderservice.service.OrderService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderControllerTest {

    @Test
    void purchase_passesBodyToService_andReturnsOkResponse() {
        OrderService svc = mock(OrderService.class);
        OrderController ctrl = new OrderController(svc);

        UUID pid = UUID.randomUUID();
        PurchaseRequest req = new PurchaseRequest(List.of(new PurchaseRequest.OrderItemRequest(pid, 3)));
        PurchaseResponse expected = new PurchaseResponse(UUID.randomUUID(), "ORD-ABC12345", new BigDecimal("123.45"));

        when(svc.purchaseProduct(any(PurchaseRequest.class), isNull())).thenReturn(Mono.just(expected));

        var respEntity = ctrl.purchase(req).block();
        assertNotNull(respEntity);
        assertEquals(200, respEntity.getStatusCode().value());
        assertEquals(expected, respEntity.getBody());

        ArgumentCaptor<PurchaseRequest> reqCap = ArgumentCaptor.forClass(PurchaseRequest.class);
        verify(svc).purchaseProduct(reqCap.capture(), isNull());
        PurchaseRequest captured = reqCap.getValue();
        assertNotNull(captured);
        assertEquals(1, captured.items().size());
        assertEquals(pid, captured.items().get(0).productId());
        assertEquals(3, captured.items().get(0).quantity());
    }

    @Test
    void history_passesPaging_andReturnsOkResponse() {
        OrderService svc = mock(OrderService.class);
        OrderController ctrl = new OrderController(svc);
        List<OrderHistoryDto> data = List.of(new OrderHistoryDto(
                UUID.randomUUID(), "ORD-1", new BigDecimal("10.00"), OrderStatus.CREATED,
                OffsetDateTime.now(), List.of()
        ));
        when(svc.getOrderHistory(null, 1, 5)).thenReturn(Mono.just(data));

        var respEntity = ctrl.history(1, 5).block();
        assertNotNull(respEntity);
        assertEquals(200, respEntity.getStatusCode().value());
        assertEquals(data, respEntity.getBody());
    }

    @Test
    void purchase_callsService_withNullToken() {
        OrderService svc = mock(OrderService.class);
        OrderController ctrl = new OrderController(svc);
        UUID pid = UUID.randomUUID();
        PurchaseRequest req = new PurchaseRequest(List.of(new PurchaseRequest.OrderItemRequest(pid, 1)));

        when(svc.purchaseProduct(any(PurchaseRequest.class), isNull()))
                .thenReturn(Mono.just(new PurchaseResponse(UUID.randomUUID(), "ORD-123", new BigDecimal("99.99"))));

        var respEntity = ctrl.purchase(req).block();
        assertNotNull(respEntity);
        assertEquals(200, respEntity.getStatusCode().value());

        ArgumentCaptor<PurchaseRequest> reqCap = ArgumentCaptor.forClass(PurchaseRequest.class);
        verify(svc).purchaseProduct(reqCap.capture(), isNull());
        assertEquals(1, reqCap.getValue().items().size());
        assertEquals(pid, reqCap.getValue().items().get(0).productId());
        assertEquals(1, reqCap.getValue().items().get(0).quantity());
    }
}