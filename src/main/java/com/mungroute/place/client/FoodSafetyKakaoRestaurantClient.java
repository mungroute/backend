package com.mungroute.place.client;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.place.config.FoodSafetyPlaceProperties;
import com.mungroute.place.config.KakaoLocalProperties;
import com.mungroute.place.exception.PlaceErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import tools.jackson.databind.JsonNode;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class FoodSafetyKakaoRestaurantClient implements PetRestaurantGateway {
    private static final Logger log = LoggerFactory.getLogger(FoodSafetyKakaoRestaurantClient.class);
    private static final String JUNG_GU_ADDRESS_PREFIX = "서울특별시 중구";
    private static final double JUNG_GU_CENTER_LONGITUDE = 126.997;
    private static final double JUNG_GU_CENTER_LATITUDE = 37.564;
    private static final int JUNG_GU_DISCOVERY_RADIUS_METERS = 8_000;
    private static final int KAKAO_PAGE_SIZE = 15;
    private static final int KAKAO_MAX_RESULT_PAGES = 3;
    private static final DateTimeFormatter SOURCE_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withLocale(Locale.KOREA)
            .withZone(ZoneId.of("Asia/Seoul"));

    private final RestClient foodSafetyClient;
    private final RestClient kakaoClient;
    private final FoodSafetyPlaceProperties foodSafetyProperties;
    private final KakaoLocalProperties kakaoProperties;
    private volatile Snapshot snapshot = Snapshot.empty();

    public FoodSafetyKakaoRestaurantClient(
            @Qualifier("foodSafetyPetRestaurantsRestClient") RestClient foodSafetyClient,
            @Qualifier("kakaoLocalRestClient") RestClient kakaoClient,
            FoodSafetyPlaceProperties foodSafetyProperties,
            KakaoLocalProperties kakaoProperties
    ) {
        this.foodSafetyClient = foodSafetyClient;
        this.kakaoClient = kakaoClient;
        this.foodSafetyProperties = foodSafetyProperties;
        this.kakaoProperties = kakaoProperties;
    }

    @Override
    public List<PetRestaurant> findJungGuRestaurants() {
        return currentSnapshot().items();
    }

    @Override
    public Optional<PetRestaurant> findById(String id) {
        return currentSnapshot().items().stream().filter(item -> item.id().equals(id)).findFirst();
    }

    private Snapshot currentSnapshot() {
        Snapshot current = snapshot;
        if (current.isFresh(foodSafetyProperties.cacheTtl())) return current;
        synchronized (this) {
            current = snapshot;
            if (current.isFresh(foodSafetyProperties.cacheTtl())) return current;
            try {
                Snapshot refreshed = refresh();
                snapshot = refreshed;
                return refreshed;
            } catch (BusinessException exception) {
                if (!current.items().isEmpty()) {
                    log.warn("Official pet restaurant refresh failed; serving the previous snapshot", exception);
                    return current;
                }
                throw exception;
            }
        }
    }

    private Snapshot refresh() {
        if (kakaoProperties.restApiKey().isBlank()) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_NOT_CONFIGURED);
        }
        List<OfficialRestaurant> officialRows = loadOfficialRows();
        String updatedAt = SOURCE_TIME.format(Instant.now());
        List<PetRestaurant> officialPlaces = officialRows.stream()
                .map(row -> enrich(row, updatedAt))
                .flatMap(Optional::stream)
                .toList();
        List<PetRestaurant> discoveredPlaces = discoverKakaoPetPlaces(updatedAt);
        Map<String, PetRestaurant> merged = officialPlaces.stream().collect(Collectors.toMap(
                this::dedupeKey, Function.identity(), (first, ignored) -> first, LinkedHashMap::new
        ));
        discoveredPlaces.forEach(place -> merged.putIfAbsent(dedupeKey(place), place));
        if (merged.isEmpty()) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
        log.info("Loaded {} Food Safety Korea pet restaurants in Seoul Jung-gu; {} matched in Kakao Local; "
                        + "{} additional Kakao keyword places discovered",
                officialRows.size(), officialPlaces.size(), merged.size() - officialPlaces.size());
        return new Snapshot(List.copyOf(merged.values()), Instant.now());
    }

    private List<OfficialRestaurant> loadOfficialRows() {
        byte[] workbook;
        try {
            workbook = foodSafetyClient.post().retrieve().body(byte[].class);
        } catch (RestClientException exception) {
            log.warn("Food Safety Korea pet restaurant export is unavailable; continuing with Kakao Local", exception);
            return List.of();
        }
        if (workbook == null || workbook.length == 0) {
            log.warn("Food Safety Korea pet restaurant export was empty; continuing with Kakao Local");
            return List.of();
        }
        try {
            return parseOfficialRows(workbook).stream()
                    .filter(row -> row.address().startsWith(JUNG_GU_ADDRESS_PREFIX))
                    .toList();
        } catch (BusinessException exception) {
            log.warn("Food Safety Korea pet restaurant export was invalid; continuing with Kakao Local");
            return List.of();
        }
    }

    private Optional<PetRestaurant> enrich(OfficialRestaurant source, String updatedAt) {
        JsonNode root;
        try {
            root = kakaoClient.get()
                    .uri("/v2/local/search/keyword.json?query={query}&x={x}&y={y}&radius=20000&size=15",
                            source.name() + " 서울 중구", JUNG_GU_CENTER_LONGITUDE, JUNG_GU_CENTER_LATITUDE)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + kakaoProperties.restApiKey())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            log.warn("Kakao Local matching failed for {}", source.name());
            return Optional.empty();
        }
        if (root == null || !root.path("documents").isArray()) return Optional.empty();

        KakaoPlace best = null;
        int bestScore = Integer.MIN_VALUE;
        for (JsonNode document : root.path("documents")) {
            KakaoPlace candidate = new KakaoPlace(
                    text(document, "place_name"), text(document, "category_name"), text(document, "phone"),
                    firstNonBlank(text(document, "road_address_name"), text(document, "address_name")),
                    text(document, "place_url"), number(document, "y"), number(document, "x")
            );
            if (candidate.latitude() == null || candidate.longitude() == null) continue;
            int score = matchScore(source, candidate);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best == null || bestScore < 3) return Optional.empty();
        return Optional.of(new PetRestaurant(
                stableId(source.name(), source.address()), source.name(),
                source.industry(), best.category(), source.address(),
                best.telephone(), best.placeUrl(), best.latitude(), best.longitude(), true, updatedAt
        ));
    }

    private List<PetRestaurant> discoverKakaoPetPlaces(String updatedAt) {
        Map<String, PetRestaurant> places = new LinkedHashMap<>();
        discoverKakaoKeyword("애견동반 카페", "CE7", updatedAt, places);
        discoverKakaoKeyword("애견동반 음식점", "FD6", updatedAt, places);
        return List.copyOf(places.values());
    }

    private void discoverKakaoKeyword(
            String query,
            String categoryGroupCode,
            String updatedAt,
            Map<String, PetRestaurant> places
    ) {
        for (int page = 1; page <= KAKAO_MAX_RESULT_PAGES; page++) {
            JsonNode root;
            try {
                root = kakaoClient.get()
                        .uri("/v2/local/search/keyword.json?query={query}&category_group_code={category}"
                                        + "&x={x}&y={y}&radius={radius}&size={size}&page={page}&sort=accuracy",
                                query, categoryGroupCode, JUNG_GU_CENTER_LONGITUDE, JUNG_GU_CENTER_LATITUDE,
                                JUNG_GU_DISCOVERY_RADIUS_METERS, KAKAO_PAGE_SIZE, page)
                        .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + kakaoProperties.restApiKey())
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RestClientException exception) {
                log.warn("Kakao Local pet place discovery failed for {} page {}", query, page);
                return;
            }
            if (root == null || !root.path("documents").isArray()) return;
            for (JsonNode document : root.path("documents")) {
                String address = firstNonBlank(text(document, "road_address_name"), text(document, "address_name"));
                Double latitude = number(document, "y");
                Double longitude = number(document, "x");
                if (address == null || !address.startsWith("서울 중구") || latitude == null || longitude == null) {
                    continue;
                }
                String name = text(document, "place_name");
                String category = text(document, "category_name");
                if (name == null) continue;
                PetRestaurant place = new PetRestaurant(
                        stableId(name, address), name, category, category, address,
                        text(document, "phone"), text(document, "place_url"), latitude, longitude,
                        false, updatedAt
                );
                places.putIfAbsent(dedupeKey(place), place);
            }
            if (root.path("meta").path("is_end").asBoolean(true)) return;
        }
    }

    private String dedupeKey(PetRestaurant place) {
        if (place.placeUrl() != null && !place.placeUrl().isBlank()) return place.placeUrl();
        return normalize(place.name()) + '|' + normalize(place.address());
    }

    private int matchScore(OfficialRestaurant source, KakaoPlace candidate) {
        int score = 0;
        String sourceName = normalize(source.name());
        String candidateName = normalize(candidate.name());
        if (sourceName.equals(candidateName)) score += 8;
        else if (sourceName.contains(candidateName) || candidateName.contains(sourceName)) score += 5;
        if (candidate.address() != null && candidate.address().contains("서울 중구")) score += 4;
        String sourceStreet = streetToken(source.address());
        if (!sourceStreet.isBlank() && candidate.address() != null && normalize(candidate.address()).contains(sourceStreet)) score += 3;
        return score;
    }

    private List<OfficialRestaurant> parseOfficialRows(byte[] workbook) {
        try {
            Map<String, byte[]> entries = unzip(workbook);
            List<String> sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml"));
            byte[] sheet = entries.get("xl/worksheets/sheet1.xml");
            if (sheet == null) throw new IllegalArgumentException("sheet1.xml is missing");
            List<List<String>> rows = parseSheet(sheet, sharedStrings);
            int headerIndex = -1;
            Map<String, Integer> columns = Map.of();
            for (int index = 0; index < Math.min(rows.size(), 10); index++) {
                Map<String, Integer> candidate = headerColumns(rows.get(index));
                if (candidate.containsKey("업소명") && candidate.containsKey("업소주소")) {
                    headerIndex = index;
                    columns = candidate;
                    break;
                }
            }
            if (headerIndex < 0) throw new IllegalArgumentException("official headers are missing");
            List<OfficialRestaurant> result = new ArrayList<>();
            for (int index = headerIndex + 1; index < rows.size(); index++) {
                List<String> row = rows.get(index);
                String name = cell(row, columns.get("업소명"));
                String address = cell(row, columns.get("업소주소"));
                if (name.isBlank() || address.isBlank()) continue;
                result.add(new OfficialRestaurant(name, cell(row, columns.get("업종")), address));
            }
            return result;
        } catch (Exception exception) {
            log.warn("Unable to parse Food Safety Korea pet restaurant workbook", exception);
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_INVALID_RESPONSE);
        }
    }

    private Map<String, byte[]> unzip(byte[] workbook) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(workbook))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private List<String> parseSharedStrings(byte[] xml) throws Exception {
        if (xml == null) return List.of();
        Document document = parseXml(xml);
        NodeList items = document.getElementsByTagName("si");
        List<String> result = new ArrayList<>(items.getLength());
        for (int index = 0; index < items.getLength(); index++) {
            NodeList texts = ((Element) items.item(index)).getElementsByTagName("t");
            StringBuilder value = new StringBuilder();
            for (int textIndex = 0; textIndex < texts.getLength(); textIndex++) {
                value.append(texts.item(textIndex).getTextContent());
            }
            result.add(value.toString());
        }
        return result;
    }

    private List<List<String>> parseSheet(byte[] xml, List<String> sharedStrings) throws Exception {
        Document document = parseXml(xml);
        NodeList rowNodes = document.getElementsByTagName("row");
        List<List<String>> rows = new ArrayList<>(rowNodes.getLength());
        for (int rowIndex = 0; rowIndex < rowNodes.getLength(); rowIndex++) {
            Element rowElement = (Element) rowNodes.item(rowIndex);
            NodeList cells = rowElement.getElementsByTagName("c");
            Map<Integer, String> values = new LinkedHashMap<>();
            int maxColumn = -1;
            for (int cellIndex = 0; cellIndex < cells.getLength(); cellIndex++) {
                Element cell = (Element) cells.item(cellIndex);
                int column = columnIndex(cell.getAttribute("r"));
                maxColumn = Math.max(maxColumn, column);
                String type = cell.getAttribute("t");
                String value = "inlineStr".equals(type)
                        ? childText(cell, "t") : childText(cell, "v");
                if ("s".equals(type) && !value.isBlank()) {
                    int sharedIndex = Integer.parseInt(value);
                    value = sharedIndex >= 0 && sharedIndex < sharedStrings.size() ? sharedStrings.get(sharedIndex) : "";
                }
                values.put(column, value == null ? "" : value.trim());
            }
            List<String> row = new ArrayList<>();
            for (int column = 0; column <= maxColumn; column++) row.add(values.getOrDefault(column, ""));
            rows.add(row);
        }
        return rows;
    }

    private Document parseXml(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(new String(xml, StandardCharsets.UTF_8))));
    }

    private Map<String, Integer> headerColumns(List<String> row) {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < row.size(); index++) columns.put(row.get(index).replaceAll("\\s+", ""), index);
        return columns;
    }

    private int columnIndex(String reference) {
        int value = 0;
        for (int index = 0; index < reference.length(); index++) {
            char character = reference.charAt(index);
            if (!Character.isLetter(character)) break;
            value = value * 26 + (Character.toUpperCase(character) - 'A' + 1);
        }
        return Math.max(0, value - 1);
    }

    private String childText(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }

    private String cell(List<String> row, Integer column) {
        return column == null || column < 0 || column >= row.size() ? "" : row.get(column).trim();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asString().isBlank() ? null : value.asString().trim();
    }

    private Double number(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) return null;
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String stableId(String name, String address) {
        CRC32 crc = new CRC32();
        crc.update((name + '|' + address).getBytes(StandardCharsets.UTF_8));
        return Long.toUnsignedString(crc.getValue());
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-z가-힣]", "");
    }

    private String streetToken(String address) {
        String normalized = normalize(address);
        int district = normalized.indexOf("중구");
        return district < 0 ? normalized : normalized.substring(Math.min(normalized.length(), district + 2));
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private record OfficialRestaurant(String name, String industry, String address) {
    }

    private record KakaoPlace(
            String name,
            String category,
            String telephone,
            String address,
            String placeUrl,
            Double latitude,
            Double longitude
    ) {
    }

    private record Snapshot(List<PetRestaurant> items, Instant loadedAt) {
        private Snapshot {
            items = List.copyOf(items);
        }

        static Snapshot empty() {
            return new Snapshot(List.of(), Instant.EPOCH);
        }

        boolean isFresh(java.time.Duration ttl) {
            return !items.isEmpty() && loadedAt.plus(ttl).isAfter(Instant.now());
        }
    }
}
