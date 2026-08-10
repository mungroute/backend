-- pgRouting 3.8+용 도보 네트워크 토폴로지 테이블
-- deprecated된 pgr_createTopology(), pgr_analyzeGraph()에 의존하지 않는다.


-- pgr_extractVertices() 결과를 영구 보관하는 정점 테이블이다.
-- route_segment.source / target은 이 테이블의 vertex_id를 사용한다.
CREATE TABLE route_vertex (
    vertex_id  BIGINT PRIMARY KEY,

    -- pgr_extractVertices()가 반환한 정점 좌표
    geom       GEOMETRY(Point, 5186) NOT NULL,
    x          DOUBLE PRECISION NOT NULL,
    y          DOUBLE PRECISION NOT NULL,

    -- 원본 edge 방향을 기준으로 정점에 들어오고 나가는 segment ID 목록
    -- 연결성 판단은 이 배열이 아니라 pgr_connectedComponents()를 사용한다.
    in_edges   BIGINT[],
    out_edges  BIGINT[]
);


-- F-16 지도 탭 좌표를 가까운 도보망 정점에 스냅할 때 사용한다.
CREATE INDEX idx_route_vertex_geom
    ON route_vertex
    USING GIST(geom);


-- data-pipeline이 정제한 edge를 최종 route_segment 적재 전에 보관한다.
-- source / target은 pgr_extractVertices() 실행 후 채우므로 여기서는 NULL을 허용한다.
CREATE TABLE route_segment_staging (
    segment_id              BIGINT PRIMARY KEY,

    source                  BIGINT,
    target                  BIGINT,

    -- Python에서 EPSG:5186 변환과 endpoint 1m 스냅을 완료한 geometry
    geom                    GEOMETRY(LineString, 5186) NOT NULL,
    length_m                NUMERIC(8,2) NOT NULL,

    -- 원본 데이터 추적과 전처리 QA를 위한 필드
    link_type_code          VARCHAR(4) NOT NULL,
    original_start_node_id  BIGINT,
    original_end_node_id    BIGINT,

    CONSTRAINT chk_route_segment_staging_length
        CHECK (length_m > 0)
);


-- pgr_extractVertices() 결과와 endpoint를 연결할 때 공간 검색을 보조한다.
CREATE INDEX idx_route_segment_staging_geom
    ON route_segment_staging
    USING GIST(geom);


-- source / target UPDATE 및 품질 검사에 사용한다.
CREATE INDEX idx_route_segment_staging_source
    ON route_segment_staging(source);

CREATE INDEX idx_route_segment_staging_target
    ON route_segment_staging(target);


COMMENT ON TABLE route_vertex IS
    'pgr_extractVertices()로 생성한 pgRouting 정점. EPSG:5186.';

COMMENT ON TABLE route_segment_staging IS
    '1m endpoint 스냅 후 source/target 연결과 검증을 수행하는 도보망 적재 staging 테이블.';

COMMENT ON COLUMN route_segment_staging.source IS
    'ST_StartPoint(geom)에 대응하는 route_vertex.vertex_id. vertex 추출 전에는 NULL.';

COMMENT ON COLUMN route_segment_staging.target IS
    'ST_EndPoint(geom)에 대응하는 route_vertex.vertex_id. vertex 추출 전에는 NULL.';
