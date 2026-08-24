package com.mungroute.place.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "curated_place_menu_item")
class CuratedPlaceMenuItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "menu_item_id", nullable = false, updatable = false)
    private Long menuItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false, updatable = false)
    private CuratedPlaceMenuEntity menu;

    @Column(name = "menu_name", length = 200, nullable = false, updatable = false)
    private String menuName;

    @Column(name = "price_won", updatable = false)
    private Long priceWon;

    @Column(name = "is_specialty", nullable = false, updatable = false)
    private boolean specialty;

    @Column(name = "image_url", length = 2_000, updatable = false)
    private String imageUrl;

    @Column(name = "display_order", nullable = false, updatable = false)
    private int displayOrder;

    protected CuratedPlaceMenuItemEntity() {
    }

    Long menuItemId() {
        return menuItemId;
    }

    String menuName() {
        return menuName;
    }

    Long priceWon() {
        return priceWon;
    }

    boolean specialty() {
        return specialty;
    }

    String imageUrl() {
        return imageUrl;
    }
}
