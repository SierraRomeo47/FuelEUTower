package com.fueleu.controltower.flexibility.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/marketplace")
@CrossOrigin(origins = "http://localhost:3000")
public class CbMarketplaceController {

    private static final String TOKEN_SYMBOL = "MCO2";
    private static final String TOKEN_ID = "moss-carbon-credit";
    private static final String OPX_URL = "https://oceanscore.com/pool-price-index/";
    private static final Pattern OPX_PRICE_PATTERN = Pattern.compile("OPX \\(2025\\)[\\s\\S]{0,200}?([0-9]{2,4}\\.[0-9]{2})");
    private static final BigDecimal CB_TO_GCO2EQ = new BigDecimal("1000000"); // 1 CB = 1 tCO2eq = 1,000,000 gCO2eq
    private static final BigDecimal DEFAULT_EUR_PER_CB = new BigDecimal("200.00"); // OPX-scale benchmark fallback
    private static final BigDecimal DEFAULT_USD_PRICE = new BigDecimal("15.00"); // legacy token fallback only
    private static final BigDecimal DEFAULT_EUR_USD = new BigDecimal("0.92"); // legacy FX fallback only

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final boolean httpEnabled;

    public CbMarketplaceController(JdbcTemplate jdbcTemplate, @Value("${fueleu.marketplace.http-enabled:true}") boolean httpEnabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.httpEnabled = httpEnabled;
    }

    @GetMapping("/cb-rate")
    public Map<String, Object> getCbRate(@RequestParam(defaultValue = "2025") int year) {
        MarketQuote quote = fetchQuote();

        jdbcTemplate.update(
                "INSERT INTO cb_market_rate_snapshot (id, market_source, market_symbol, quoted_price_eur_per_gco2eq, quoted_price_usd_per_token, fx_eur_usd) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                quote.source,
                quote.symbol,
                quote.priceEurPerGco2eq,
                quote.priceUsdPerToken,
                quote.fxEurUsd
        );

        Map<String, Object> out = new HashMap<>();
        out.put("year", year);
        out.put("source", quote.source);
        out.put("symbol", quote.symbol);
        out.put("priceUsdPerToken", quote.priceUsdPerToken.setScale(4, RoundingMode.HALF_UP));
        out.put("fxEurUsd", quote.fxEurUsd.setScale(6, RoundingMode.HALF_UP));
        out.put("cbToGco2eq", CB_TO_GCO2EQ);
        // Compliance calculations remain in gCO2eq. Marketplace quote is normalized to CB/tCO2eq.
        out.put("priceEurPerGco2eq", quote.priceEurPerGco2eq.setScale(8, RoundingMode.HALF_UP));
        out.put("priceEurPerCb", quote.priceEurPerCb.setScale(4, RoundingMode.HALF_UP));
        out.put("priceEurPerTco2eq", quote.priceEurPerCb.setScale(4, RoundingMode.HALF_UP));
        out.put("disclaimer", "Indicative benchmark from public sources (OPX preferred). Final executable rates may vary by counterparty and lot size.");
        return out;
    }

    @PostMapping("/purchase")
    @Transactional
    public ResponseEntity<Map<String, Object>> purchaseCbs(@RequestBody Map<String, Object> payload) {
        String vesselIdRaw = payload.get("vesselId") == null ? null : payload.get("vesselId").toString();
        String amountCbRaw = payload.get("amountCb") == null ? null : payload.get("amountCb").toString();
        String amountGco2eqRaw = payload.get("amountGco2eq") == null ? null : payload.get("amountGco2eq").toString();
        int year = payload.get("year") == null ? 2025 : Integer.parseInt(payload.get("year").toString());
        if (vesselIdRaw == null || (amountCbRaw == null && amountGco2eqRaw == null)) {
            return badRequest("Missing required fields: vesselId and amountCb (or legacy amountGco2eq).");
        }

        UUID vesselId;
        BigDecimal amountCb;
        BigDecimal amountGco2eq;
        try {
            vesselId = UUID.fromString(vesselIdRaw);
            if (amountCbRaw != null) {
                amountCb = new BigDecimal(amountCbRaw);
                amountGco2eq = amountCb.multiply(CB_TO_GCO2EQ);
            } else {
                amountGco2eq = new BigDecimal(amountGco2eqRaw);
                amountCb = amountGco2eq.divide(CB_TO_GCO2EQ, 8, RoundingMode.HALF_UP);
            }
        } catch (Exception ex) {
            return badRequest("Invalid vesselId or amount.");
        }
        if (amountCb.compareTo(BigDecimal.ZERO) <= 0 || amountGco2eq.compareTo(BigDecimal.ZERO) <= 0) {
            return badRequest("Amount must be positive.");
        }

        UUID periodId;
        try {
            periodId = jdbcTemplate.queryForObject("SELECT id FROM reporting_period WHERE year = ? LIMIT 1", UUID.class, year);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            periodId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO reporting_period (id, year, status, ghg_limit) VALUES (?, ?, 'OPEN', 89.3368)", periodId, year);
        }

        UUID vesselYearId;
        try {
            vesselYearId = jdbcTemplate.queryForObject("SELECT id FROM vessel_year WHERE vessel_id = ? AND reporting_period_id = ? LIMIT 1", UUID.class, vesselId, periodId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            vesselYearId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO vessel_year (id, vessel_id, reporting_period_id) VALUES (?, ?, ?)", vesselYearId, vesselId, periodId);
        }

        MarketQuote quote = fetchQuote();
        BigDecimal totalCost = amountCb.multiply(quote.priceEurPerCb).setScale(2, RoundingMode.HALF_UP);
        jdbcTemplate.update(
                "INSERT INTO cb_market_trade (id, vessel_year_id, cb_amount_gco2eq, unit_price_eur_per_gco2eq, total_cost_eur, market_source, market_symbol) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                vesselYearId,
                amountGco2eq,
                quote.priceEurPerGco2eq,
                totalCost,
                quote.source,
                quote.symbol
        );

        Map<String, Object> out = new HashMap<>();
        out.put("status", "SUCCESS");
        out.put("message", "CB purchase recorded.");
        out.put("amountCb", amountCb.setScale(4, RoundingMode.HALF_UP));
        out.put("amountGco2eq", amountGco2eq.setScale(2, RoundingMode.HALF_UP));
        out.put("cbToGco2eq", CB_TO_GCO2EQ);
        out.put("unitPriceEurPerCb", quote.priceEurPerCb.setScale(4, RoundingMode.HALF_UP));
        out.put("unitPriceEurPerGco2eq", quote.priceEurPerGco2eq.setScale(8, RoundingMode.HALF_UP));
        out.put("totalCostEur", totalCost);
        out.put("source", quote.source);
        out.put("symbol", quote.symbol);
        return ResponseEntity.ok(out);
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "BAD_REQUEST");
        response.put("message", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    private MarketQuote fetchQuote() {
        if (!httpEnabled) {
            BigDecimal eurPerGco2eq = DEFAULT_EUR_PER_CB.divide(CB_TO_GCO2EQ, 12, RoundingMode.HALF_UP);
            return new MarketQuote("disabled-http-default", "OPX", BigDecimal.ZERO, BigDecimal.ZERO, eurPerGco2eq, DEFAULT_EUR_PER_CB);
        }
        // Prefer OPX benchmark first (FuelEU-specific pooling market signal).
        try {
            HttpRequest opxReq = HttpRequest.newBuilder()
                    .uri(URI.create(OPX_URL))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            HttpResponse<String> opxResp = httpClient.send(opxReq, HttpResponse.BodyHandlers.ofString());
            if (opxResp.statusCode() == 200) {
                Matcher m = OPX_PRICE_PATTERN.matcher(opxResp.body());
                if (m.find()) {
                    BigDecimal eurPerCb = new BigDecimal(m.group(1)); // 1 CB = 1 tCO2eq
                    BigDecimal eurPerGco2eq = eurPerCb.divide(CB_TO_GCO2EQ, 12, RoundingMode.HALF_UP);
                    return new MarketQuote("oceanscore-opx", "OPX", BigDecimal.ZERO, BigDecimal.ZERO, eurPerGco2eq, eurPerCb);
                }
            }
        } catch (Exception ignored) {
        }

        // If OPX fetch fails, prefer OPX-scale static fallback (FuelEU pooling convention) rather than unrelated token spot.
        BigDecimal fallbackEurPerGco2eq = DEFAULT_EUR_PER_CB.divide(CB_TO_GCO2EQ, 12, RoundingMode.HALF_UP);

        BigDecimal usd = DEFAULT_USD_PRICE;
        BigDecimal eurUsd = DEFAULT_EUR_USD;
        String source = "fallback-static";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.coingecko.com/api/v3/simple/price?ids=" + TOKEN_ID + "&vs_currencies=usd"))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode usdNode = root.path(TOKEN_ID).path("usd");
                if (usdNode.isNumber()) {
                    usd = usdNode.decimalValue();
                    source = "coingecko";
                }
            }
        } catch (Exception ignored) {
        }

        try {
            HttpRequest fxReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.er-api.com/v6/latest/USD"))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            HttpResponse<String> fxResp = httpClient.send(fxReq, HttpResponse.BodyHandlers.ofString());
            if (fxResp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(fxResp.body());
                JsonNode eurNode = root.path("rates").path("EUR");
                if (eurNode.isNumber()) {
                    eurUsd = eurNode.decimalValue();
                    if ("fallback-static".equals(source)) {
                        source = "open-er-api+fallback-token";
                    } else {
                        source = source + "+open-er-api";
                    }
                }
            }
        } catch (Exception ignored) {
        }

        BigDecimal tokenEurPerCb = usd.multiply(eurUsd);
        BigDecimal eurPerCb = tokenEurPerCb.max(DEFAULT_EUR_PER_CB);
        BigDecimal eurPerGco2eq = eurPerCb.divide(CB_TO_GCO2EQ, 12, RoundingMode.HALF_UP);
        String outSource = "coingecko".equals(source) ? "coingecko+bounded-opx" : "static-opx-fallback";
        return new MarketQuote(outSource, TOKEN_SYMBOL, usd, eurUsd, eurPerGco2eq, eurPerCb);
    }

    private record MarketQuote(
            String source,
            String symbol,
            BigDecimal priceUsdPerToken,
            BigDecimal fxEurUsd,
            BigDecimal priceEurPerGco2eq,
            BigDecimal priceEurPerCb
    ) {}
}
