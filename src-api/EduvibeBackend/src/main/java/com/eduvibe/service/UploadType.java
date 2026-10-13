package com.eduvibe.service;

import com.eduvibe.exception.BadRequestException;

/**
 * Qué se está subiendo: decide qué extensiones y qué tamaño máximo admite
 * {@link FileStorageService}. No es un enumerado de base de datos —nada lo
 * persiste—, así que no lleva el patrón EnumConValor de los demás.
 */
public enum UploadType {
    IMAGE,
    DOCUMENT;

    public static UploadType fromValue(String value) {
        if ("imagen".equalsIgnoreCase(value)) {
            return IMAGE;
        }
        if (value == null || "documento".equalsIgnoreCase(value)) {
            return DOCUMENT;
        }
        throw new BadRequestException("El tipo debe ser imagen o documento");
    }
}
