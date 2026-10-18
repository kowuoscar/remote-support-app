package com.remotesupport.backend.repository;

import java.util.UUID;

/** A Carrier Invoice File retention must delete: its row id and the object key to delete. */
public record DueCarrierInvoiceFile(UUID id, String storagePath) {}
