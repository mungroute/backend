package com.mungroute.place.client;

import com.mungroute.place.config.FoodSafetyPlaceProperties;
import com.mungroute.place.config.KakaoLocalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class FoodSafetyKakaoRestaurantClientTest {
    private MockRestServiceServer foodSafetyServer;
    private MockRestServiceServer kakaoServer;
    private FoodSafetyKakaoRestaurantClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder foodSafetyBuilder = RestClient.builder().baseUrl("https://food.test/export");
        RestClient.Builder kakaoBuilder = RestClient.builder().baseUrl("https://kakao.test");
        foodSafetyServer = MockRestServiceServer.bindTo(foodSafetyBuilder).build();
        kakaoServer = MockRestServiceServer.bindTo(kakaoBuilder).build();
        client = new FoodSafetyKakaoRestaurantClient(
                foodSafetyBuilder.build(), kakaoBuilder.build(),
                new FoodSafetyPlaceProperties("https://food.test/export", Duration.ofHours(6)),
                new KakaoLocalProperties("https://kakao.test", "test-key")
        );
    }

    @Test
    void filtersOfficialWorkbookToJungGuAndEnrichesWithKakao() throws Exception {
        foodSafetyServer.expect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(workbook(), MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
        kakaoServer.expect(header("Authorization", "KakaoAK test-key"))
                .andExpect(queryParam("query", "97.7%ED%99%94%EC%94%A8%20%EC%84%9C%EC%9A%B8%20%EC%A4%91%EA%B5%AC"))
                .andRespond(withSuccess("""
                        {
                          "documents": [{
                            "place_name": "97.7화씨",
                            "category_name": "음식점 > 카페",
                            "phone": "02-1234-5678",
                            "road_address_name": "서울 중구 난계로15길 41-7",
                            "place_url": "https://place.map.kakao.com/123",
                            "x": "127.021",
                            "y": "37.568"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));
        kakaoServer.expect(header("Authorization", "KakaoAK test-key"))
                .andExpect(queryParam("query", "%EC%95%A0%EA%B2%AC%EB%8F%99%EB%B0%98%20%EC%B9%B4%ED%8E%98"))
                .andExpect(queryParam("category_group_code", "CE7"))
                .andExpect(queryParam("page", "1"))
                .andRespond(withSuccess("""
                        {"meta":{"is_end":true},"documents":[]}
                        """, MediaType.APPLICATION_JSON));
        kakaoServer.expect(header("Authorization", "KakaoAK test-key"))
                .andExpect(queryParam("query", "%EC%95%A0%EA%B2%AC%EB%8F%99%EB%B0%98%20%EC%9D%8C%EC%8B%9D%EC%A0%90"))
                .andExpect(queryParam("category_group_code", "FD6"))
                .andExpect(queryParam("page", "1"))
                .andRespond(withSuccess("""
                        {"meta":{"is_end":true},"documents":[]}
                        """, MediaType.APPLICATION_JSON));

        var result = client.findJungGuRestaurants();

        assertThat(result).singleElement().satisfies(place -> {
            assertThat(place.name()).isEqualTo("97.7화씨");
            assertThat(place.industry()).isEqualTo("일반음식점");
            assertThat(place.kakaoCategory()).isEqualTo("음식점 > 카페");
            assertThat(place.address()).startsWith("서울특별시 중구");
            assertThat(place.telephone()).isEqualTo("02-1234-5678");
            assertThat(place.latitude()).isEqualTo(37.568);
            assertThat(place.longitude()).isEqualTo(127.021);
            assertThat(place.placeUrl()).isEqualTo("https://place.map.kakao.com/123");
        });
        assertThat(client.findJungGuRestaurants()).isEqualTo(result);
        foodSafetyServer.verify();
        kakaoServer.verify();
    }

    @Test
    void continuesWithKakaoDiscoveryWhenFoodSafetyExportIsUnavailable() {
        foodSafetyServer.expect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withServerError());
        kakaoServer.expect(header("Authorization", "KakaoAK test-key"))
                .andExpect(queryParam("query", "%EC%95%A0%EA%B2%AC%EB%8F%99%EB%B0%98%20%EC%B9%B4%ED%8E%98"))
                .andRespond(withSuccess("""
                        {
                          "meta":{"is_end":true},
                          "documents":[{
                            "place_name":"중구 반려견 카페",
                            "category_name":"음식점 > 카페",
                            "phone":"02-1111-2222",
                            "road_address_name":"서울 중구 을지로 10",
                            "place_url":"https://place.map.kakao.com/456",
                            "x":"126.998",
                            "y":"37.565"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));
        kakaoServer.expect(header("Authorization", "KakaoAK test-key"))
                .andExpect(queryParam("query", "%EC%95%A0%EA%B2%AC%EB%8F%99%EB%B0%98%20%EC%9D%8C%EC%8B%9D%EC%A0%90"))
                .andRespond(withSuccess("""
                        {"meta":{"is_end":true},"documents":[]}
                        """, MediaType.APPLICATION_JSON));

        var result = client.findJungGuRestaurants();

        assertThat(result).singleElement().satisfies(place -> {
            assertThat(place.name()).isEqualTo("중구 반려견 카페");
            assertThat(place.officiallyRegistered()).isFalse();
            assertThat(place.placeUrl()).isEqualTo("https://place.map.kakao.com/456");
        });
        foodSafetyServer.verify();
        kakaoServer.verify();
    }

    private byte[] workbook() throws Exception {
        String[] strings = {
                "연번", "업소명", "업종", "지역", "업소주소",
                "1", "97.7화씨", "일반음식점", "서울", "서울특별시 중구 난계로15길 41-7",
                "2", "타지역 카페", "휴게음식점", "서울특별시 마포구 월드컵로 1"
        };
        StringBuilder shared = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><sst>");
        for (String value : strings) shared.append("<si><t>").append(value).append("</t></si>");
        shared.append("</sst>");
        String sheet = """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet><sheetData>
                  <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c><c r="E1" t="s"><v>4</v></c></row>
                  <row r="2"><c r="A2" t="s"><v>5</v></c><c r="B2" t="s"><v>6</v></c><c r="C2" t="s"><v>7</v></c><c r="D2" t="s"><v>8</v></c><c r="E2" t="s"><v>9</v></c></row>
                  <row r="3"><c r="A3" t="s"><v>10</v></c><c r="B3" t="s"><v>11</v></c><c r="C3" t="s"><v>12</v></c><c r="D3" t="s"><v>8</v></c><c r="E3" t="s"><v>13</v></c></row>
                </sheetData></worksheet>
                """;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            write(zip, "xl/sharedStrings.xml", shared.toString());
            write(zip, "xl/worksheets/sheet1.xml", sheet);
        }
        return output.toByteArray();
    }

    private void write(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
