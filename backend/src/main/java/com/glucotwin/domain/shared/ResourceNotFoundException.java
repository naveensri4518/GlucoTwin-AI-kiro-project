package com.glucotwin.domain.shared;

public class ResourceNotFoundException extends GlucoTwinException {
    public ResourceNotFoundException(String resourceType, String id) {
        super(resourceType + " not found: " + id);
    }
}
