package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PasswordTest {

    private static final PasswordEncoder PASSWORD_ENCODER = new FakePasswordEncoder();

    @Nested
    class 생성 {

        @ParameterizedTest
        @ValueSource(strings = {
                "Abcd123!", // 최소 길이 8자
                "Abcd1234!Abcd123", // 최대 길이 16자
                "Abcd1234!@#" // 일반 케이스
        })
        void 유효한_비밀번호면_통과한다(String password) {
            assertThatCode(() -> Password.of(password, PASSWORD_ENCODER))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        void 비밀번호가_null_또는_빈값이면_예외(String password) {
            assertThatThrownBy(() -> Password.of(password, PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("비밀번호는 필수");
        }

        @Test
        void 비밀번호가_8자_미만이면_예외() {
            assertThatThrownBy(() -> Password.of("Abcd123", PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("비밀번호는 8~16자여야 합니다");
        }

        @Test
        void 비밀번호가_16자_초과면_예외() {
            assertThatThrownBy(() -> Password.of("Abcd1234Abcd1234!", PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("비밀번호는 8~16자여야 합니다");
        }

        @Test
        void 비밀번호에_허용되지_않은_문자가_포함되면_예외() {
            assertThatThrownBy(() -> Password.of("가Abcd1234!", PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("비밀번호는 영문/숫자/특수문자만 가능합니다");
        }
    }

    @Nested
    class 변경 {

        @Test
        void 유효한_새_비밀번호로_변경하면_새_객체를_반환한다() {
            Password password = Password.of("Abcd1234!", PASSWORD_ENCODER);

            Password changed = password.change("Efgh5678!", PASSWORD_ENCODER);

            assertThat(changed).isNotSameAs(password);
        }

        @Test
        void 현재_비밀번호와_동일하면_예외() {
            Password password = Password.of("Abcd1234!", PASSWORD_ENCODER);

            assertThatThrownBy(() -> password.change("Abcd1234!", PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("현재 비밀번호와 동일한 비밀번호는 사용할 수 없습니다");
        }

        @Test
        void 유효하지_않은_비밀번호로_변경하면_예외() {
            Password password = Password.of("Abcd1234!", PASSWORD_ENCODER);

            assertThatThrownBy(() -> password.change("short", PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    class 매칭 {

        @Test
        void 동일한_비밀번호면_true() {
            Password password = Password.of("Abcd1234!", PASSWORD_ENCODER);

            assertThat(password.matches("Abcd1234!", PASSWORD_ENCODER)).isTrue();
        }

        @Test
        void 다른_비밀번호면_false() {
            Password password = Password.of("Abcd1234!", PASSWORD_ENCODER);

            assertThat(password.matches("Efgh5678!", PASSWORD_ENCODER)).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        void 비밀번호가_null_또는_빈값이면_예외(String rawPassword) {
            Password password = Password.of("Abcd1234!", PASSWORD_ENCODER);

            assertThatThrownBy(() -> password.matches(rawPassword, PASSWORD_ENCODER))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("비밀번호는 필수");
        }
    }
}
