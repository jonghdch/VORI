package com.vori.backend.paymentinstrument;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentInstrumentRepository extends JpaRepository<PaymentInstrument, Long> {

    List<PaymentInstrument> findByUserIdAndArchivedAtIsNullOrderByIdAsc(Long userId);

    long countByUserIdAndArchivedAtIsNull(Long userId);
}
