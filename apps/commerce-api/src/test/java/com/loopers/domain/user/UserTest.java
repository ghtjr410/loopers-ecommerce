package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserTest {

    private static final PasswordEncoder PASSWORD_ENCODER = new FakePasswordEncoder();

    @Nested
    class 생성 {

        @Test
        void 유효한_값이면_회원이_생성된다() {
            String loginId = "testuser123";
            String rawPassword = "Test1234!";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(2000, 1, 15);
            String email = "test@example.com";

            User user = User.create(loginId, rawPassword, name, birthDate, email, PASSWORD_ENCODER);

            assertThat(user.getLoginId()).isEqualTo(loginId);
            assertThat(user.matchesPassword(rawPassword, PASSWORD_ENCODER)).isTrue();
            assertThat(user.getName()).isEqualTo(name);
            assertThat(user.getBirthDate()).isEqualTo(birthDate);
            assertThat(user.getEmail()).isEqualTo(email);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void 로그인ID가_null_또는_빈값이면_예외(String loginId) {
            assertThatThrownBy(() -> User.create(loginId, "Test1234!", "홍길동", LocalDate.of(2000, 1, 15), "test@example.com", PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("로그인 ID");
        }

        @ParameterizedTest
        @ValueSource(strings = {"test user", "test@user", "test-user", "테스트유저", "test_user"})
        void 로그인ID가_영문숫자가_아니면_예외(String loginId) {
            assertThatThrownBy(() -> User.create(loginId, "Test1234!", "홍길동", LocalDate.of(2000, 1, 15), "test@example.com", PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("로그인 ID는 영문/숫자만 가능합니다");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void 이름이_null_또는_빈값이면_예외(String name) {
            assertThatThrownBy(() -> User.create("testuser", "Test1234!", name, LocalDate.of(2000, 1, 15), "test@example.com", PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("이름");
        }

        @Test
        void 생년월일이_null이면_예외() {
            assertThatThrownBy(() -> User.create("testuser", "Test1234!", "홍길동", null, "test@example.com", PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("생년월일");
        }

        @Test
        void 생년월일이_미래면_예외() {
            LocalDate futureDate = LocalDate.of(2999, 1, 1);

            assertThatThrownBy(() -> User.create("testuser", "Test1234!", "홍길동", futureDate, "test@example.com", PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("생년월일은 미래일 수 없습니다");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void 이메일이_null_또는_빈값이면_예외(String email) {
            assertThatThrownBy(() -> User.create("testuser", "Test1234!", "홍길동", LocalDate.of(2000, 1, 15), email, PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("이메일");
        }

        @ParameterizedTest
        @ValueSource(strings = {"invalid", "@domain.com", "user@", "user@.com", "user@domain"})
        void 이메일이_형식에_맞지_않으면_예외(String email) {
            assertThatThrownBy(() -> User.create("testuser", "Test1234!", "홍길동", LocalDate.of(2000, 1, 15), email, PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("올바른 이메일 형식이 아닙니다");
        }

        @ParameterizedTest
        @ValueSource(strings = {"Abcd20000115!", "Abcd000115!!", "Abcd0115"})
        void 비밀번호에_생년월일이_포함되면_예외(String rawPassword) {
            assertThatThrownBy(() -> User.create("testuser", rawPassword, "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com", PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("생년월일을 포함할 수 없습니다");
        }
    }

    @Nested
    class 이름_마스킹 {

        @ParameterizedTest
        @CsvSource({
                "홍길동, 홍길*",
                "김밥, 김*",
                "이, *"
        })
        void 마지막_글자를_마스킹한다(String name, String expected) {
            User user = User.create("testuser", "Test1234!", name, LocalDate.of(2000, 1, 15), "test@example.com", PASSWORD_ENCODER);

            assertThat(user.getMaskedName()).isEqualTo(expected);
        }
    }

    @Nested
    class 비밀번호_변경 {

        @Test
        void 유효한_새_비밀번호면_변경된다() {
            User user = User.create("testuser", "Test1234!", "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com", PASSWORD_ENCODER);

            assertThatCode(() -> user.changePassword("NewPass123!", PASSWORD_ENCODER))
                    .doesNotThrowAnyException();
        }

        @Test
        void 비밀번호에_생년월일이_포함되면_예외() {
            User user = User.create("testuser", "Test1234!", "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com", PASSWORD_ENCODER);

            assertThatThrownBy(() -> user.changePassword("Pass0115!!", PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("생년월일을 포함할 수 없습니다");
        }
    }
}
