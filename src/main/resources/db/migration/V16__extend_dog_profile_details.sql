ALTER TABLE dog_profile
    ADD COLUMN gender VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN neutered BOOLEAN,
    ADD COLUMN introduction VARCHAR(50),
    ADD COLUMN leash_greeting VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN stranger_response VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN touch_tolerance VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN barking_level VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN biting_level VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE dog_profile
    ADD CONSTRAINT chk_dog_gender CHECK (gender IN ('MALE', 'FEMALE', 'UNKNOWN')),
    ADD CONSTRAINT chk_dog_leash_greeting CHECK (leash_greeting IN ('LIKES', 'NEUTRAL', 'DIFFICULT', 'UNKNOWN')),
    ADD CONSTRAINT chk_dog_stranger_response CHECK (stranger_response IN ('LIKES', 'NEUTRAL', 'DIFFICULT', 'UNKNOWN')),
    ADD CONSTRAINT chk_dog_touch_tolerance CHECK (touch_tolerance IN ('COMFORTABLE', 'CONDITIONAL', 'DIFFICULT', 'UNKNOWN')),
    ADD CONSTRAINT chk_dog_barking_level CHECK (barking_level IN ('RARE', 'NORMAL', 'FREQUENT', 'UNKNOWN')),
    ADD CONSTRAINT chk_dog_biting_level CHECK (biting_level IN ('NONE', 'CONDITIONAL', 'PRESENT', 'UNKNOWN'));
