package com.mungroute.place.client;

import tools.jackson.databind.JsonNode;
import com.mungroute.global.exception.BusinessException;
import com.mungroute.place.config.KtoPetTourProperties;
import com.mungroute.place.exception.PlaceErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Component
public class KtoPetTourClient implements PetTourGateway {
    static final String FOOD_CONTENT_TYPE_ID = "39";

    private static final Logger log = LoggerFactory.getLogger(KtoPetTourClient.class);

    private final RestClient restClient;
    private final KtoPetTourProperties properties;

    public KtoPetTourClient(RestClient ktoPetTourRestClient, KtoPetTourProperties properties) {
        this.restClient = ktoPetTourRestClient;
        this.properties = properties;
    }

    @Override
    public PetTourPage<PetTourListItem> findNearbyRestaurants(
            double latitude,
            double longitude,
            int radiusMeters,
            int page,
            int size
    ) {
        JsonNode body = request("locationBasedList2", builder -> builder
                .queryParam("numOfRows", size)
                .queryParam("pageNo", page)
                .queryParam("arrange", "S")
                .queryParam("contentTypeId", FOOD_CONTENT_TYPE_ID)
                .queryParam("mapX", longitude)
                .queryParam("mapY", latitude)
                .queryParam("radius", radiusMeters));
        return listPage(body);
    }

    @Override
    public PetTourPage<PetTourListItem> findAreaRestaurants(
            String regionCode,
            String districtCode,
            int page,
            int size
    ) {
        JsonNode body = request("areaBasedList2", builder -> builder
                .queryParam("numOfRows", size)
                .queryParam("pageNo", page)
                .queryParam("arrange", "A")
                .queryParam("contentTypeId", FOOD_CONTENT_TYPE_ID)
                .queryParam("lDongRegnCd", regionCode)
                .queryParam("lDongSignguCd", districtCode));
        return listPage(body);
    }

    @Override
    public PetTourPage<PetTourListItem> searchRestaurants(String keyword, int page, int size) {
        JsonNode body = request("searchKeyword2", builder -> builder
                .queryParam("numOfRows", size)
                .queryParam("pageNo", page)
                .queryParam("arrange", "A")
                .queryParam("contentTypeId", FOOD_CONTENT_TYPE_ID)
                .queryParam("keyword", keyword));
        return listPage(body);
    }

    @Override
    public Optional<PetTourCommon> findCommon(String contentId) {
        JsonNode item = firstItem(request("detailCommon2", builder -> builder
                .queryParam("numOfRows", 1)
                .queryParam("pageNo", 1)
                .queryParam("contentId", contentId))).orElse(null);
        if (item == null) return Optional.empty();
        return Optional.of(new PetTourCommon(
                text(item, "contentid"), text(item, "title"), text(item, "addr1"),
                text(item, "addr2"), text(item, "tel"), text(item, "homepage"),
                text(item, "firstimage"), text(item, "firstimage2"), text(item, "cpyrhtDivCd"),
                number(item, "mapx"), number(item, "mapy"), text(item, "overview")
        ));
    }

    @Override
    public Optional<PetTourFoodIntro> findFoodIntro(String contentId) {
        JsonNode item = firstItem(request("detailIntro2", builder -> builder
                .queryParam("numOfRows", 1)
                .queryParam("pageNo", 1)
                .queryParam("contentId", contentId)
                .queryParam("contentTypeId", FOOD_CONTENT_TYPE_ID))).orElse(null);
        if (item == null) return Optional.empty();
        return Optional.of(new PetTourFoodIntro(
                text(item, "opentimefood"), text(item, "restdatefood"),
                text(item, "firstmenu"), text(item, "treatmenu"),
                text(item, "parkingfood"), text(item, "packing"),
                text(item, "reservationfood"), text(item, "chkcreditcardfood")
        ));
    }

    @Override
    public Optional<PetTourPetDetail> findPetDetail(String contentId) {
        JsonNode item = firstItem(request("detailPetTour2", builder -> builder
                .queryParam("numOfRows", 1)
                .queryParam("pageNo", 1)
                .queryParam("contentId", contentId))).orElse(null);
        if (item == null) return Optional.empty();
        return Optional.of(new PetTourPetDetail(
                text(item, "acmpyTypeCd"), text(item, "acmpyPsblCpam"),
                text(item, "acmpyNeedMtr"), text(item, "relaAcdntRiskMtr"),
                text(item, "relaPosesFclty"), text(item, "relaFrnshPrdlst"),
                text(item, "relaPurcPrdlst"), text(item, "relaRntlPrdlst"),
                text(item, "etcAcmpyInfo")
        ));
    }

    @Override
    public List<PetTourImage> findImages(String contentId) {
        JsonNode body = request("detailImage2", builder -> builder
                .queryParam("numOfRows", 20)
                .queryParam("pageNo", 1)
                .queryParam("contentId", contentId)
                .queryParam("imageYN", "Y"));
        return itemNodes(body).stream()
                .map(item -> new PetTourImage(
                        text(item, "imgname"), text(item, "originimgurl"),
                        text(item, "smallimageurl"), text(item, "cpyrhtDivCd"),
                        text(item, "serialnum")
                ))
                .filter(image -> image.originalUrl() != null)
                .toList();
    }

    private JsonNode request(String operation, Consumer<org.springframework.web.util.UriBuilder> query) {
        if (properties.serviceKey().isBlank()) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_NOT_CONFIGURED);
        }
        try {
            JsonNode root = restClient.get()
                    .uri(builder -> {
                        builder.pathSegment(operation)
                                .queryParam("serviceKey", properties.serviceKey())
                                .queryParam("MobileOS", properties.mobileOs())
                                .queryParam("MobileApp", properties.mobileApp())
                                .queryParam("_type", "json");
                        query.accept(builder);
                        URI uri = builder.build();
                        return uri;
                    })
                    .retrieve()
                    .body(JsonNode.class);
            return responseBody(root, operation);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("KTO Pet Tour request failed: operation={}", operation, exception);
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
    }

    private JsonNode responseBody(JsonNode root, String operation) {
        JsonNode response = root == null ? null : root.path("response");
        String resultCode = response == null ? "" : response.path("header").path("resultCode").asText("");
        if (!"0000".equals(resultCode) && !"00".equals(resultCode) && !"0".equals(resultCode)) {
            String resultMessage = response == null ? "" : response.path("header").path("resultMsg").asText("");
            log.warn("KTO Pet Tour returned an error: operation={}, code={}, message={}", operation, resultCode, resultMessage);
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_INVALID_RESPONSE);
        }
        return response.path("body");
    }

    private PetTourPage<PetTourListItem> listPage(JsonNode body) {
        List<PetTourListItem> items = itemNodes(body).stream().map(item -> new PetTourListItem(
                text(item, "contentid"), text(item, "contenttypeid"), text(item, "title"), text(item, "addr1"),
                text(item, "addr2"), text(item, "tel"), text(item, "firstimage"),
                text(item, "firstimage2"), text(item, "cpyrhtDivCd"), number(item, "mapx"),
                number(item, "mapy"), number(item, "dist"), text(item, "lclsSystm1"),
                text(item, "lclsSystm2"), text(item, "lclsSystm3")
        )).toList();
        return new PetTourPage<>(items, body.path("pageNo").asInt(1),
                body.path("numOfRows").asInt(items.size()), body.path("totalCount").asInt(items.size()));
    }

    private Optional<JsonNode> firstItem(JsonNode body) {
        List<JsonNode> nodes = itemNodes(body);
        return nodes.isEmpty() ? Optional.empty() : Optional.of(nodes.getFirst());
    }

    private List<JsonNode> itemNodes(JsonNode body) {
        JsonNode node = body.path("items").path("item");
        if (node.isMissingNode() || node.isNull()) return List.of();
        if (node.isArray()) {
            List<JsonNode> result = new ArrayList<>();
            node.forEach(result::add);
            return result;
        }
        return node.isObject() ? List.of(node) : List.of();
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static Double number(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) return null;
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
