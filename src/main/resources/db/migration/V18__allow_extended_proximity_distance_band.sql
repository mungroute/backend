ALTER TABLE proximity_event
    DROP CONSTRAINT chk_proximity_event_distance_band;

ALTER TABLE proximity_event
    ADD CONSTRAINT chk_proximity_event_distance_band
        CHECK (
            distance_band IN (
                'VERY_CLOSE',
                'BAND_30_50',
                'BAND_50_100',
                'BAND_100_500'
            )
        );
