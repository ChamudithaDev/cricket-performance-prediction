package com.cricketprediction.backend;

import com.cricketprediction.backend.model.Player;
import com.cricketprediction.backend.model.Role;
import com.cricketprediction.backend.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for entity construction, UserDetails contract, and PasswordEncoder.
 *
 * NO Spring context is loaded — these tests execute instantly and do not require
 * MySQL, a DataSource bean, or JPA.
 *
 * The @SpringBootTest smoke test lives in AppContextSmokeTest.java.
 */
@ExtendWith(MockitoExtension.class)
class BackendApplicationTests {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ------------------------------------------------------------------
    // PasswordEncoder tests
    // ------------------------------------------------------------------

    @Test
    void passwordEncoderHashesPassword() {
        String raw = "TestPassword123";
        String hashed = passwordEncoder.encode(raw);

        assertThat(hashed).isNotNull();
        assertThat(hashed).isNotEqualTo(raw);
        assertThat(passwordEncoder.matches(raw, hashed)).isTrue();
    }

    @Test
    void differentPasswordsDoNotMatch() {
        String hashed = passwordEncoder.encode("CorrectPassword");
        assertThat(passwordEncoder.matches("WrongPassword", hashed)).isFalse();
    }

    // ------------------------------------------------------------------
    // User entity / UserDetails contract tests
    // ------------------------------------------------------------------

    @Test
    void userEntityBuildsCorrectly() {
        User user = User.builder()
                .email("test@example.com")
                .password(passwordEncoder.encode("password123"))
                .fullName("Test User")
                .role(Role.ROLE_USER)
                .build();

        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getFullName()).isEqualTo("Test User");
        assertThat(user.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(user.getUsername()).isEqualTo("test@example.com");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
    }

    @Test
    void userAuthoritiesContainRole() {
        User user = User.builder()
                .email("admin@example.com")
                .password("hashed")
                .fullName("Admin")
                .role(Role.ROLE_ADMIN)
                .build();

        assertThat(user.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    // ------------------------------------------------------------------
    // Player entity tests
    // ------------------------------------------------------------------

    @Test
    void playerEntityBuildsCorrectly() {
        Player player = Player.builder()
                .name("V Kohli")
                .fullName("Virat Kohli")
                .dateOfBirth(java.time.LocalDate.of(1988, 11, 5))
                .nationality("India")
                .profileImageUrl("https://example.com/kohli.jpg")
                .role("Batter")
                .battingStyle("Right-hand bat")
                .bowlingStyle("Right-arm medium")
                .currentTeam("Royal Challengers Bengaluru")
                .build();

        assertThat(player.getName()).isEqualTo("V Kohli");
        assertThat(player.getFullName()).isEqualTo("Virat Kohli");
        assertThat(player.getDateOfBirth()).isEqualTo(java.time.LocalDate.of(1988, 11, 5));
        assertThat(player.getNationality()).isEqualTo("India");
        assertThat(player.getProfileImageUrl()).isEqualTo("https://example.com/kohli.jpg");
        assertThat(player.getRole()).isEqualTo("Batter");
        assertThat(player.getBattingStyle()).isEqualTo("Right-hand bat");
        assertThat(player.getBowlingStyle()).isEqualTo("Right-arm medium");
        assertThat(player.getCurrentTeam()).isEqualTo("Royal Challengers Bengaluru");
    }

    @Test
    void playerEntitySupportsNullableFields() {
        Player player = Player.builder()
                .name("V Kohli")
                .build();

        assertThat(player.getName()).isEqualTo("V Kohli");
        assertThat(player.getFullName()).isNull();
        assertThat(player.getDateOfBirth()).isNull();
        assertThat(player.getNationality()).isNull();
        assertThat(player.getProfileImageUrl()).isNull();
        assertThat(player.getRole()).isNull();
        assertThat(player.getBattingStyle()).isNull();
        assertThat(player.getBowlingStyle()).isNull();
        assertThat(player.getCurrentTeam()).isNull();
    }
}

