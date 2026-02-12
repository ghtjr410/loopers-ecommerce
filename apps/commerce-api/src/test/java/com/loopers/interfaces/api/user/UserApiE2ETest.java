package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserApiE2ETest {

    private static final String SIGNUP_ENDPOINT = "/api/v1/users";
    private static final String MY_INFO_ENDPOINT = "/api/v1/users/me";
    private static final String CHANGE_PASSWORD_ENDPOINT = "/api/v1/users/me/password";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 회원가입 {

        @Test
        void 유효한_정보로_회원가입하면_회원정보가_반환된다() {
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "testuser", "Test1234!", "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com"
            );

            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = postSignUp(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser"),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                    () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(2000, 1, 15)),
                    () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
            );
        }

        @Test
        void 이미_존재하는_로그인ID로_가입하면_409_응답() {
            // arrange
            signUp("testuser", "Test1234!", "홍길동", LocalDate.of(2000, 1, 15), "test@example.com");

            UserV1Dto.SignUpRequest duplicateRequest = new UserV1Dto.SignUpRequest(
                    "testuser", "Test5678!", "김철수",
                    LocalDate.of(1995, 5, 20), "other@example.com"
            );

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(duplicateRequest),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 유효하지_않은_입력이면_400_응답() {
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "test-user!", "Test1234!", "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com"
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class 내_정보_조회 {

        @Test
        void 유효한_인증정보로_조회하면_마스킹된_이름과_함께_정보가_반환된다() {
            signUp("testuser", "Test1234!", "홍길동", LocalDate.of(2000, 1, 15), "test@example.com");

            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = getMyInfo("testuser", "Test1234!");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser"),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                    () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(2000, 1, 15)),
                    () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
            );
        }

        @Test
        void 존재하지_않는_로그인ID로_조회하면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    MY_INFO_ENDPOINT, HttpMethod.GET,
                    new HttpEntity<>(authHeaders("notexist", "Test1234!")),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 비밀번호가_일치하지_않으면_401_응답() {
            signUp("testuser", "Test1234!", "홍길동", LocalDate.of(2000, 1, 15), "test@example.com");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    MY_INFO_ENDPOINT, HttpMethod.GET,
                    new HttpEntity<>(authHeaders("testuser", "WrongPass1!")),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 인증헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    MY_INFO_ENDPOINT, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class 비밀번호_변경 {

        @Test
        void 유효한_새_비밀번호로_변경하면_새_비밀번호로_인증할_수_있다() {
            // arrange
            signUp("testuser", "Test1234!", "홍길동", LocalDate.of(2000, 1, 15), "test@example.com");

            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("NewPass123!");

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    CHANGE_PASSWORD_ENDPOINT, HttpMethod.PATCH,
                    new HttpEntity<>(request, authHeaders("testuser", "Test1234!")),
                    new ParameterizedTypeReference<>() {}
            );

            // assert - 변경 성공
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // assert - 새 비밀번호로 인증 가능
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> verifyResponse = getMyInfo("testuser", "NewPass123!");
            assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 현재_비밀번호와_동일한_비밀번호로_변경하면_400_응답() {
            // arrange
            signUp("testuser", "Test1234!", "홍길동", LocalDate.of(2000, 1, 15), "test@example.com");

            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("Test1234!");

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    CHANGE_PASSWORD_ENDPOINT, HttpMethod.PATCH,
                    new HttpEntity<>(request, authHeaders("testuser", "Test1234!")),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증_실패하면_401_응답() {
            // arrange
            signUp("testuser", "Test1234!", "홍길동", LocalDate.of(2000, 1, 15), "test@example.com");

            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("NewPass123!");

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    CHANGE_PASSWORD_ENDPOINT, HttpMethod.PATCH,
                    new HttpEntity<>(request, authHeaders("testuser", "WrongPass1!")),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 인증헤더가_누락되면_401_응답() {
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("NewPass123!");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    CHANGE_PASSWORD_ENDPOINT, HttpMethod.PATCH,
                    new HttpEntity<>(request, new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // --- 헬퍼 메서드 ---

    private void signUp(String loginId, String password, String name, LocalDate birthDate, String email) {
        UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(loginId, password, name, birthDate, email);
        postSignUp(request);
    }

    private ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> postSignUp(UserV1Dto.SignUpRequest request) {
        return testRestTemplate.exchange(
                SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> getMyInfo(String loginId, String password) {
        return testRestTemplate.exchange(
                MY_INFO_ENDPOINT, HttpMethod.GET,
                new HttpEntity<>(authHeaders(loginId, password)),
                new ParameterizedTypeReference<>() {}
        );
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }
}
