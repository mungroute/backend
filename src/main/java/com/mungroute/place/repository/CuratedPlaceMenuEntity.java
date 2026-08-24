package com.mungroute.place.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Immutable
@Table(name = "curated_place_menu")
class CuratedPlaceMenuEntity {
    @Id
    @Column(name = "content_id", length = 20, nullable = false, updatable = false)
    private String contentId;

    @Column(name = "place_name", length = 200, nullable = false, updatable = false)
    private String placeName;

    @Column(name = "source_label", length = 100, nullable = false, updatable = false)
    private String sourceLabel;

    @Column(name = "source_url", length = 2_000, updatable = false)
    private String sourceUrl;

    @Column(name = "verified_on", nullable = false, updatable = false)
    private LocalDate verifiedOn;

    @Column(name = "verification_note", length = 500, updatable = false)
    private String verificationNote;

    @OneToMany(mappedBy = "menu", fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC, menuItemId ASC")
    private List<CuratedPlaceMenuItemEntity> items = new ArrayList<>();

    protected CuratedPlaceMenuEntity() {
    }

    String contentId() {
        return contentId;
    }

    String placeName() {
        return placeName;
    }

    String sourceLabel() {
        return sourceLabel;
    }

    String sourceUrl() {
        return sourceUrl;
    }

    LocalDate verifiedOn() {
        return verifiedOn;
    }

    String verificationNote() {
        return verificationNote;
    }

    List<CuratedPlaceMenuItemEntity> items() {
        return items;
    }
}
