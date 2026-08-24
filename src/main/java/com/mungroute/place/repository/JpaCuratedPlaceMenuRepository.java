package com.mungroute.place.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class JpaCuratedPlaceMenuRepository implements CuratedPlaceMenuRepository {
    private final SpringDataCuratedPlaceMenuRepository repository;

    public JpaCuratedPlaceMenuRepository(SpringDataCuratedPlaceMenuRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CuratedPlaceMenu> findByContentId(String contentId) {
        return repository.findWithItemsByContentId(contentId).map(this::toRecord);
    }

    private CuratedPlaceMenu toRecord(CuratedPlaceMenuEntity menu) {
        return new CuratedPlaceMenu(
                menu.contentId(),
                menu.placeName(),
                menu.sourceLabel(),
                menu.sourceUrl(),
                menu.verifiedOn(),
                menu.verificationNote(),
                menu.items().stream()
                        .map(item -> new CuratedMenuItem(
                                item.menuName(),
                                item.priceWon(),
                                item.specialty(),
                                item.imageUrl()
                        ))
                        .toList()
        );
    }
}
