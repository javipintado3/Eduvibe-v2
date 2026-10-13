package com.eduvibe.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.eduvibe.model.enums.UserRole;

class InvitationTest {

    private Invitation invitationThatExpiresIn(Duration deadline) {
        User user = new User(new Organization("Centro", null), "ana@centro.es", "Ana", UserRole.STUDENT);
        return new Invitation(user, "hash", Instant.now().plus(deadline));
    }

    @Test
    @DisplayName("una invitación recién emitida sirve")
    void reciénIssuedServes() {
        assertThat(invitationThatExpiresIn(Duration.ofHours(48)).isUsable()).isTrue();
    }

    @Test
    @DisplayName("una invitación caducada no sirve")
    void caducadaNoSirve() {
        Invitation invitation = invitationThatExpiresIn(Duration.ofHours(-1));

        assertThat(invitation.hasExpired()).isTrue();
        assertThat(invitation.isUsable()).isFalse();
    }

    @Test
    @DisplayName("una invitación solo sirve una vez")
    void unSoloUso() {
        Invitation invitation = invitationThatExpiresIn(Duration.ofHours(48));

        invitation.markAsUsed();

        assertThat(invitation.isUsed()).isTrue();
        assertThat(invitation.isUsable()).isFalse();
    }
}
