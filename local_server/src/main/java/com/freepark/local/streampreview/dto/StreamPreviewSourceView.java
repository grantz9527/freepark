package com.freepark.local.streampreview.dto;

public record StreamPreviewSourceView(String name, String label) {

    public StreamPreviewSourceView {
        name = name == null ? "" : name.trim();
        String trimmedLabel = label == null ? "" : label.trim();
        label = trimmedLabel.isEmpty() ? name : trimmedLabel;
    }

    public boolean matches(String input) {
        if (input == null || input.isBlank() || name.isEmpty()) {
            return false;
        }
        if (name.equalsIgnoreCase(input)) {
            return true;
        }
        return label != null && !label.isBlank() && label.equals(input);
    }
}
