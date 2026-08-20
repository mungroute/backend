CREATE TABLE redtable_restaurant (
    restaurant_id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    road_address VARCHAR(1000),
    lot_address VARCHAR(1000),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    telephone VARCHAR(50),
    business_type VARCHAR(100),
    license_name VARCHAR(100),
    introduction TEXT,
    synced_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_redtable_restaurant_location
    ON redtable_restaurant(latitude, longitude);

CREATE INDEX idx_redtable_restaurant_name
    ON redtable_restaurant(name);

CREATE TABLE redtable_menu (
    menu_id BIGINT PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES redtable_restaurant(restaurant_id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    price BIGINT,
    specialty BOOLEAN NOT NULL DEFAULT FALSE,
    image_url VARCHAR(4000),
    synced_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_redtable_menu_restaurant
    ON redtable_menu(restaurant_id, specialty DESC, menu_id);

CREATE TABLE redtable_restaurant_image (
    restaurant_id BIGINT NOT NULL REFERENCES redtable_restaurant(restaurant_id) ON DELETE CASCADE,
    image_url VARCHAR(4000) NOT NULL,
    display_order INTEGER NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (restaurant_id, image_url)
);

CREATE INDEX idx_redtable_restaurant_image_order
    ON redtable_restaurant_image(restaurant_id, display_order);

CREATE TABLE redtable_sync_state (
    dataset VARCHAR(50) PRIMARY KEY,
    completed_at TIMESTAMPTZ NOT NULL,
    restaurant_count INTEGER NOT NULL,
    menu_count INTEGER NOT NULL,
    image_count INTEGER NOT NULL
);

COMMENT ON TABLE redtable_restaurant IS '레드테이블 서울 중구 식당 캐시';
COMMENT ON TABLE redtable_menu IS '레드테이블 서울 중구 메뉴 캐시';
COMMENT ON TABLE redtable_restaurant_image IS '레드테이블 서울 중구 식당 이미지 캐시';
