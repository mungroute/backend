package com.mungroute.place.client;

import com.mungroute.global.exception.BusinessException;
import com.mungroute.place.config.KtoPetTourProperties;
import com.mungroute.place.exception.PlaceErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KtoPetTourClientTest {
    private MockRestServiceServer server;
    private KtoPetTourClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://apis.data.go.kr/B551011/KorPetTourService2");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KtoPetTourClient(builder.build(), new KtoPetTourProperties(
                "https://apis.data.go.kr/B551011/KorPetTourService2",
                "test-key", "ETC", "MungRoute"
        ));
    }

    @Test
    void requestsFoodTypeAndParsesNearbyRestaurants() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).endsWith("/locationBasedList2");
                    String query = request.getURI().getQuery();
                    assertThat(query).contains("serviceKey=test-key");
                    assertThat(query).contains("contentTypeId=39");
                    assertThat(query).contains("arrange=S");
                    assertThat(query).contains("mapX=127.04");
                    assertThat(query).contains("mapY=37.55");
                })
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        PetTourGateway.PetTourPage<PetTourGateway.PetTourListItem> result =
                client.findNearbyRestaurants(37.55, 127.04, 2000, 1, 20);

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.contentId()).isEqualTo("3012345");
            assertThat(item.contentTypeId()).isEqualTo("39");
            assertThat(item.title()).isEqualTo("멍카페 성수");
            assertThat(item.distanceMeters()).isEqualTo(620.4);
        });
        server.verify();
    }

    @Test
    void requestsOnlySeoulJungGuRestaurantsFromTheAreaEndpoint() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).endsWith("/areaBasedList2");
                    String query = request.getURI().getQuery();
                    assertThat(query).contains("contentTypeId=39");
                    assertThat(query).contains("lDongRegnCd=11");
                    assertThat(query).contains("lDongSignguCd=140");
                    assertThat(query).contains("arrange=A");
                })
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        PetTourGateway.PetTourPage<PetTourGateway.PetTourListItem> result =
                client.findAreaRestaurants("11", "140", 1, 100);

        assertThat(result.items()).singleElement().extracting(PetTourGateway.PetTourListItem::contentId)
                .isEqualTo("3012345");
        server.verify();
    }

    @Test
    void mapsProviderErrorToStableBusinessError() {
        server.expect(request -> assertThat(request.getURI().getPath()).endsWith("/searchKeyword2"))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"22","resultMsg":"LIMIT EXCEEDED"}}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.searchRestaurants("카페", 1, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(PlaceErrorCode.PLACE_PROVIDER_INVALID_RESPONSE));
    }

    private String successResponse() {
        return """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {
                      "items": {"item": [{
                        "contentid": "3012345",
                        "contenttypeid": "39",
                        "title": "멍카페 성수",
                        "addr1": "서울특별시 성동구 성수이로 1",
                        "firstimage": "https://example.com/place.jpg",
                        "mapx": "127.041",
                        "mapy": "37.551",
                        "dist": "620.4"
                      }]},
                      "numOfRows": 20,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """;
    }
}
