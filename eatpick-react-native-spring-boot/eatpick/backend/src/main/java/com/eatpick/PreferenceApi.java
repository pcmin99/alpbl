package com.eatpick;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/me/recommendation-preferences")
public class PreferenceApi {

    private final PreferenceService preferences;

    public PreferenceApi(PreferenceService preferences) {
        this.preferences = preferences;
    }

    public record PreferenceInput(
            @DecimalMin("1") @DecimalMax("1000000") BigDecimal budgetPerServing,

            @NotNull Boolean conveniencePreferred,

            @NotNull @Size(max = 100) List<@NotBlank @Size(max = 36) String> excludedProductIds) {
    }

    @GetMapping
    public PreferenceService.Preference get(
            Authentication authentication) {
        return preferences.load(
                authentication.getName());
    }

    @PutMapping
    public PreferenceService.Preference save(
            Authentication authentication,
            @Valid @RequestBody PreferenceInput input) {
        return preferences.save(
                authentication.getName(),
                input.budgetPerServing(),
                input.conveniencePreferred(),
                input.excludedProductIds());
    }
}