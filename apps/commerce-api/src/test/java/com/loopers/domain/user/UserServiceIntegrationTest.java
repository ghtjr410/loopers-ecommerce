package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 회원가입 {

        @Test
        void 유효한_정보로_회원가입하면_회원이_생성된다() {
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(2000, 1, 15);
            String email = "test@example.com";

            User result = userService.signUp(loginId, rawPassword, name, birthDate, email);

            assertThat(result.getLoginId()).isEqualTo(loginId);
            assertThat(result.getName()).isEqualTo(name);
            assertThat(result.getBirthDate()).isEqualTo(birthDate);
            assertThat(result.getEmail()).isEqualTo(email);
        }

        @Test
        void 이미_존재하는_로그인ID로_가입하면_예외() {
            String loginId = "testuser";
            userService.signUp(loginId, "Test1234!", "홍길동", LocalDate.of(2000, 1, 15), "test@example.com");

            assertThatThrownBy(() -> userService.signUp(loginId, "Test5678!", "김철수", LocalDate.of(1995, 5, 20), "other@example.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT))
                    .hasMessageContaining("이미 사용 중인 로그인 ID입니다");
        }
    }

    @Nested
    class 인증 {

        @Test
        void 유효한_인증정보로_인증하면_회원을_반환한다() {
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            userService.signUp(loginId, rawPassword, "홍길동", LocalDate.of(2000, 1, 15), "test@example.com");

            User user = userService.authenticate(loginId, rawPassword);

            assertThat(user.getLoginId()).isEqualTo(loginId);
        }

        @Test
        void 존재하지_않는_로그인ID로_인증하면_예외() {
            assertThatThrownBy(() -> userService.authenticate("notexist", "Test1234!"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED))
                    .hasMessageContaining("아이디 또는 비밀번호가 일치하지 않습니다");
        }

        @Test
        void 비밀번호가_일치하지_않으면_예외() {
            String loginId = "testuser";
            userService.signUp(loginId, "Test1234!", "홍길동", LocalDate.of(2000, 1, 15), "test@example.com");

            assertThatThrownBy(() -> userService.authenticate(loginId, "WrongPass1!"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED))
                    .hasMessageContaining("아이디 또는 비밀번호가 일치하지 않습니다");
        }
    }

    @Nested
    class 비밀번호_변경 {

        @Test
        void 유효한_새_비밀번호로_변경하면_성공한다() {
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            String newPassword = "NewPass123!";
            User user = userService.signUp(loginId, rawPassword, "홍길동", LocalDate.of(2000, 1, 15), "test@example.com");

            userService.changePassword(user.getId(), newPassword);

            assertThatCode(() -> userService.authenticate(loginId, newPassword))
                    .doesNotThrowAnyException();
        }

        @Test
        void 변경_후_이전_비밀번호로_인증하면_예외() {
            String loginId = "testuser";
            User user = userService.signUp(loginId, "Test1234!", "홍길동", LocalDate.of(2000, 1, 15), "test@example.com");

            userService.changePassword(user.getId(), "NewPass123!");

            assertThatThrownBy(() -> userService.authenticate(loginId, "Test1234!"))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    class 회원_조회 {

        @Test
        void 존재하지_않는_ID로_조회하면_예외() {
            assertThatThrownBy(() -> userService.getById(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }
}
