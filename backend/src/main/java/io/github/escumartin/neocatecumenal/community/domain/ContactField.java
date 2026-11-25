package io.github.escumartin.neocatecumenal.community.domain;

public enum ContactField {
    MOBILE("mobile"),
    LANDLINE("landline"),
    EMAIL("email");

    private final String key;

    ContactField(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CommunityFieldInvalid(key, "REQUIRED");
        }
        try {
            return this == EMAIL ? EmailAddress.of(raw).value() : PhoneNumber.of(raw).value();
        } catch (CommunityFieldInvalid invalid) {
            throw new CommunityFieldInvalid(key, invalid.code());
        }
    }
}
