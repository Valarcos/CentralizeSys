package com.centralizesys.model.dto;

import java.time.LocalDateTime;

public record BackupFileDTO(
        String filename,
        LocalDateTime date,
        long sizeBytes,
        String type // "DB" or "EXCEL"
) {
}