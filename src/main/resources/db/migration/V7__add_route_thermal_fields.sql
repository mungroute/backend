ALTER TABLE route_segment
    ADD COLUMN surface_temp_09_c NUMERIC(5, 2),
    ADD COLUMN surface_temp_12_c NUMERIC(5, 2),
    ADD COLUMN surface_temp_15_c NUMERIC(5, 2),
    ADD COLUMN surface_temp_18_c NUMERIC(5, 2),
    ADD COLUMN surface_temp_peak_c NUMERIC(5, 2),
    ADD COLUMN thermal_model_confidence VARCHAR(10),
    ADD COLUMN thermal_weather_date DATE,
    ADD COLUMN thermal_updated_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_route_segment_thermal_model_confidence
        CHECK (
            thermal_model_confidence IS NULL
            OR thermal_model_confidence IN ('LOW', 'MEDIUM', 'HIGH')
        );
