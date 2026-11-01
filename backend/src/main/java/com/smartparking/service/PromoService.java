package com.smartparking.service;

import com.smartparking.dto.PromoDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.model.PromoCode;
import com.smartparking.repository.PromoCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Admin management of promo codes (v3). */
@Service
public class PromoService {

    private final PromoCodeRepository promoCodeRepository;

    public PromoService(PromoCodeRepository promoCodeRepository) {
        this.promoCodeRepository = promoCodeRepository;
    }

    public static PromoDtos.PromoResponse toResponse(PromoCode p) {
        return new PromoDtos.PromoResponse(
                p.getId(), p.getCode(), p.getType().name(), p.getValue(),
                p.getMaxUses(), p.getUsedCount(), p.getExpiresAt(),
                p.getIsActive(), p.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<PromoDtos.PromoResponse> list() {
        return promoCodeRepository.findAll().stream().map(PromoService::toResponse).toList();
    }

    @Transactional
    public PromoDtos.PromoResponse create(PromoDtos.PromoRequest req) {
        String code = req.code().trim().toUpperCase();
        if (promoCodeRepository.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("Promo code already exists: " + code);
        }
        PromoCode p = new PromoCode(code, req.type(), req.value(), req.maxUses(), req.expiresAt(),
                req.isActive() == null ? Boolean.TRUE : req.isActive());
        return toResponse(promoCodeRepository.save(p));
    }

    @Transactional
    public PromoDtos.PromoResponse deactivate(Long id) {
        PromoCode p = promoCodeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Promo code not found: " + id));
        p.setIsActive(false);
        return toResponse(promoCodeRepository.save(p));
    }
}
