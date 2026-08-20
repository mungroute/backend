ALTER TABLE meet_profile
    ALTER COLUMN profile_image_url TYPE TEXT,
    ADD COLUMN leash_greeting VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN stranger_response VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN touch_tolerance VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN barking_level VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN biting_level VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE meet_profile
    ADD CONSTRAINT chk_meet_profile_leash_greeting
        CHECK (leash_greeting IN ('LIKES', 'NEUTRAL', 'DIFFICULT', 'UNKNOWN')),
    ADD CONSTRAINT chk_meet_profile_stranger_response
        CHECK (stranger_response IN ('LIKES', 'NEUTRAL', 'DIFFICULT', 'UNKNOWN')),
    ADD CONSTRAINT chk_meet_profile_touch_tolerance
        CHECK (touch_tolerance IN ('COMFORTABLE', 'CONDITIONAL', 'DIFFICULT', 'UNKNOWN')),
    ADD CONSTRAINT chk_meet_profile_barking_level
        CHECK (barking_level IN ('RARE', 'NORMAL', 'FREQUENT', 'UNKNOWN')),
    ADD CONSTRAINT chk_meet_profile_biting_level
        CHECK (biting_level IN ('NONE', 'CONDITIONAL', 'PRESENT', 'UNKNOWN'));
