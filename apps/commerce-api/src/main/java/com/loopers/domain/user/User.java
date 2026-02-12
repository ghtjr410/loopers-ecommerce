package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@Entity
@Table(name = "users")
@Getter
public class User extends BaseEntity {

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    @Column(nullable = false, unique = true)
    private String loginId;

    @Embedded
    @Getter(AccessLevel.NONE)
    private Password password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate; // yyyy-MM-dd

    @Column(nullable = false)
    private String email;

    protected User() {}

    private User(String loginId, Password password, String name, LocalDate birthDate, String email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static User create(String loginId, String rawPassword, String name, LocalDate birthDate, String email, PasswordEncoder encoder) {
        validateLoginId(loginId);
        validateBirthDate(birthDate);
        validateName(name);
        validateEmail(email);

        validatePasswordNotContainsBirthDate(rawPassword, birthDate);

        Password password = Password.of(rawPassword, encoder);
        return new User(loginId, password, name, birthDate, email);
    }

    public void changePassword(String newRawPassword, PasswordEncoder encoder) {
        validatePasswordNotContainsBirthDate(newRawPassword, birthDate);
        this.password = password.change(newRawPassword, encoder);
    }

    public boolean matchesPassword(String rawPassword, PasswordEncoder encoder) {
        return password.matches(rawPassword, encoder);
    }

    public String getMaskedName() {
        return name.substring(0, name.length() - 1) + "*";
    }

    private static void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST,"로그인 ID는 필수입니다");
        }
        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST,"로그인 ID는 영문/숫자만 가능합니다");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST,"이름은 필수입니다");
        }
    }

    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST,"생년월일은 필수입니다");
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST,"생년월일은 미래일 수 없습니다");
        }
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST,"이메일은 필수입니다");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST,"올바른 이메일 형식이 아닙니다");
        }
    }

    private static void validatePasswordNotContainsBirthDate(String rawPassword, LocalDate birthDate) {
        if (rawPassword == null || birthDate == null) {
            return;
        }

        String yyyyMMdd = birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String yyMMdd = birthDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
        String MMdd = birthDate.format(DateTimeFormatter.ofPattern("MMdd"));

        if (rawPassword.contains(yyyyMMdd) || rawPassword.contains(yyMMdd) || rawPassword.contains(MMdd)) {
            throw new CoreException(ErrorType.BAD_REQUEST,"비밀번호에 생년월일을 포함할 수 없습니다");
        }
    }
}
