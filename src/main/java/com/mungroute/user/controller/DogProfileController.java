package com.mungroute.user.controller;

import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.user.dto.request.DogProfileRequest;
import com.mungroute.user.dto.response.DogProfileResponse;
import com.mungroute.user.service.DogProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/users/me/dogs")
public class DogProfileController {
    private final DogProfileService service;

    public DogProfileController(DogProfileService service) {
        this.service = service;
    }

    @GetMapping
    public List<DogProfileResponse> list(@AuthenticationPrincipal MungrouteUserPrincipal user) {
        return service.list(user.userId());
    }

    @GetMapping("/{dogId}")
    public DogProfileResponse get(@AuthenticationPrincipal MungrouteUserPrincipal user,
                                  @PathVariable @Positive long dogId) {
        return service.get(user.userId(), dogId);
    }

    @PostMapping
    public ResponseEntity<DogProfileResponse> create(@AuthenticationPrincipal MungrouteUserPrincipal user,
                                                     @Valid @RequestBody DogProfileRequest request) {
        DogProfileResponse created = service.create(user.userId(), request);
        return ResponseEntity.created(URI.create("/api/users/me/dogs/" + created.dogId())).body(created);
    }

    @PutMapping("/{dogId}")
    public DogProfileResponse update(@AuthenticationPrincipal MungrouteUserPrincipal user,
                                     @PathVariable @Positive long dogId,
                                     @Valid @RequestBody DogProfileRequest request) {
        return service.update(user.userId(), dogId, request);
    }

    @DeleteMapping("/{dogId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal MungrouteUserPrincipal user,
                                       @PathVariable @Positive long dogId) {
        service.delete(user.userId(), dogId);
        return ResponseEntity.noContent().build();
    }
}
