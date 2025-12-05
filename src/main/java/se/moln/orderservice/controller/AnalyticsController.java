package se.moln.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.moln.orderservice.model.Order;
import se.moln.orderservice.model.OrderItem;
import se.moln.orderservice.model.OrderStatus;
import se.moln.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AnalyticsController {

    private final OrderRepository orders;

    @GetMapping("/analytics/monthly-kpis")
    @Operation(
            summary = "Enkel analytics",
            description = "Returnerar antal sålda enheter, intäkter och mest populär produkt för angiven månad (standard är innevarande månad).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(
                            schema = @Schema(implementation = MonthlyKpisResponse.class),
                            examples = @ExampleObject(name = "Example",
                                    value = """
                                            {
                                              "yearMonth": "2025-09",
                                              "unitsSold": 134,
                                              "revenue":  "12990.00",
                                              "topProduct": {
                                                "productId": "de305d54-75b4-431b-adb2-eb6b9e546014",
                                                "productName": "USB-C Hub 8-in-1",
                                                "unitsSold": 42,
                                                "revenue": "4199.00"
                                              }
                                            }
                                            """)
                    ))
            }
    )
    public MonthlyKpisResponse monthlyKpis(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @Min(1) @Max(12) Integer month
    ) {
        // No authentication required

        YearMonth ym = (year == null || month == null)
                ? YearMonth.now()
                : YearMonth.of(year, month);

        // Använd samma offset som "nu" för att passa OffsetDateTime-fält
        var offset = OffsetDateTime.now().getOffset();
        OffsetDateTime from = ym.atDay(1).atStartOfDay().atOffset(offset);
        OffsetDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay().atOffset(offset);

        // Hämta endast CREATED ordrar inom intervallet
        List<Order> created = orders.findByStatusAndOrderDateBetween(OrderStatus.CREATED, from, to);

        // unitsSold = summa av alla item.quantity
        int unitsSold = created.stream()
                .flatMap(o -> o.getOrderItems().stream())
                .mapToInt(OrderItem::getQuantity)
                .sum();

        // revenue = summa av order.totalAmount
        BigDecimal revenue = created.stream()
                .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // topProduct = flest sålda enheter
        record Agg(BigDecimal revenue, int units, String name) {
        }
        Map<UUID, Agg> byProduct = new HashMap<>();
        for (Order o : created) {
            for (OrderItem it : o.getOrderItems()) {
                UUID pid = it.getProductId();
                BigDecimal line = (it.getPriceAtPurchase() == null ? BigDecimal.ZERO : it.getPriceAtPurchase())
                        .multiply(BigDecimal.valueOf(it.getQuantity()));
                Agg prev = byProduct.getOrDefault(pid, new Agg(BigDecimal.ZERO, 0, it.getProductName()));
                byProduct.put(pid, new Agg(prev.revenue.add(line), prev.units + it.getQuantity(), it.getProductName()));
            }
        }

        ProductStat top = byProduct.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().units, a.getValue().units))
                .map(e -> new ProductStat(e.getKey(), e.getValue().name,
                        e.getValue().units,
                        e.getValue().revenue.setScale(2, RoundingMode.HALF_UP)))
                .findFirst()
                .orElse(null);

        return new MonthlyKpisResponse(ym.toString(), unitsSold, revenue, top);
    }


    public record MonthlyKpisResponse(
            String yearMonth,
            int unitsSold,
            String revenue,   // serialiseras som sträng för exakt pengar-format
            ProductStat topProduct
    ) {
        public MonthlyKpisResponse(String ym, int units, BigDecimal revenue, ProductStat top) {
            this(ym, units, revenue.setScale(2, RoundingMode.HALF_UP).toPlainString(), top);
        }
    }

    public record ProductStat(UUID productId, String productName, int unitsSold, String revenue) {
        public ProductStat(UUID id, String name, int units, BigDecimal revenue) {
            this(id, name, units, revenue.setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
    }
}