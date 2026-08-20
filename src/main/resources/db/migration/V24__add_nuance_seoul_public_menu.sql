UPDATE curated_place_menu
SET source_label = '다이닝코드 공개 정보',
    source_url = 'https://www.diningcode.com/list.dc?query=%EC%84%9C%EC%9A%B8%ED%8A%B9%EB%B3%84+%EC%A4%91%EA%B5%AC+%EB%8B%A4%EC%82%B0%EB%A1%9C38%EA%B8%B8',
    verification_note = '일부 메뉴명만 확인되어 가격은 매장 확인 필요'
WHERE content_id = '3511875071';

INSERT INTO curated_place_menu_item
    (content_id, menu_name, price_won, is_specialty, display_order)
VALUES
    ('3511875071', '휘낭시에', NULL, true, 1),
    ('3511875071', '치즈케이크', NULL, false, 2),
    ('3511875071', '커피', NULL, false, 3);
