package se.moln.orderservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.moln.orderservice.model.Order;
import se.moln.orderservice.model.OrderItem;
import se.moln.orderservice.model.OrderStatus;
import se.moln.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    OrderRepository orders;

    @InjectMocks
    AnalyticsController controller;

    private static OrderItem item(UUID productId, String name, int qty, String price) {
        OrderItem oi = new OrderItem();
        oi.setProductId(productId);
        oi.setProductName(name);
        oi.setQuantity(qty);
        oi.setPriceAtPurchase(new BigDecimal(price));
        return oi;
    }

    private static Order order(OffsetDateTime when, BigDecimal total, OrderItem... items) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setOrderDate(when);
        o.setStatus(OrderStatus.COMPLETED);
        o.setTotalAmount(total);
        // link items to order
        for (OrderItem it : items) {
            it.setOrder(o);
        }
        o.setOrderItems(List.of(items));
        return o;
    }

    @Test
    void monthlyKpis_returnsAggregatedStats_forCurrentMonth() {
        YearMonth ym = YearMonth.now();
        var offset = OffsetDateTime.now().getOffset();
        OffsetDateTime d1 = ym.atDay(5).atStartOfDay().atOffset(offset);
        OffsetDateTime d2 = ym.atDay(18).atStartOfDay().atOffset(offset);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        Order o1 = order(d1, new BigDecimal("100.00"),
                item(p1, "USB-C Hub 8-in-1", 2, "25.00"),
                item(p2, "HDMI Cable", 3, "10.00")
        );
        Order o2 = order(d2, new BigDecimal("59.99"),
                item(p1, "USB-C Hub 8-in-1", 1, "25.00")
        );

        when(orders.findByStatusAndOrderDateBetween(eq(OrderStatus.CREATED), any(), any()))
                .thenReturn(List.of(o1, o2));

        AnalyticsController.MonthlyKpisResponse resp = controller.monthlyKpis(null, null);

        assertEquals(ym.toString(), resp.yearMonth());
        assertEquals(6, resp.unitsSold()); // 2 + 3 + 1
        assertEquals("159.99", resp.revenue()); // 100.00 + 59.99
        assertNotNull(resp.topProduct());
        // There is a tie on units (p1=3, p2=3). Verify reported units and that product is one of the tied ones.
        assertEquals(3, resp.topProduct().unitsSold());
        assertTrue(resp.topProduct().productId().equals(p1) || resp.topProduct().productId().equals(p2));
    }

    @Test
    void monthlyKpis_withSpecifiedYearMonth_andNullPricesHandled() {
        int year = 2024;
        int month = 12;
        var offset = OffsetDateTime.now().getOffset();
        OffsetDateTime d = YearMonth.of(year, month).atDay(10).atStartOfDay().atOffset(offset);

        UUID p = UUID.randomUUID();

        OrderItem it = item(p, "Widget", 5, "0");
        it.setPriceAtPurchase(null); // ensure null price handled as zero

        Order o = order(d, null, it); // totalAmount null -> treated as zero in revenue sum

        when(orders.findByStatusAndOrderDateBetween(eq(OrderStatus.CREATED), any(), any()))
                .thenReturn(List.of(o));

        AnalyticsController.MonthlyKpisResponse resp = controller.monthlyKpis(year, month);

        assertEquals("2024-12", resp.yearMonth());
        assertEquals(5, resp.unitsSold());
        assertEquals("0.00", resp.revenue());
        assertNotNull(resp.topProduct());
        assertEquals(p, resp.topProduct().productId());
        assertEquals("0.00", resp.topProduct().revenue());
    }

    @Test
    void monthlyKpis_returnsEmptyStats_whenNoOrders() {
        when(orders.findByStatusAndOrderDateBetween(eq(OrderStatus.CREATED), any(), any()))
                .thenReturn(List.of());

        AnalyticsController.MonthlyKpisResponse resp = controller.monthlyKpis(null, null);

        assertNotNull(resp);
        assertEquals(YearMonth.now().toString(), resp.yearMonth());
        assertEquals(0, resp.unitsSold());
        assertEquals("0.00", resp.revenue());
        assertNull(resp.topProduct());
    }
}