package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.regex.Pattern;

@Embeddable
public class Password {
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;
    private static final Pattern ALLOWED_CHARS_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?~`]+$");

    @Column(name = "password", nullable = false)
    private String value;

    protected Password() {}

    private Password(String value) {
        this.value = value;
    }

    public static Password of(String rawPassword, PasswordEncoder encoder) {
        validate(rawPassword);
        return new Password(encoder.encode(rawPassword));
    }

    public Password change(String newRawPassword, PasswordEncoder encoder) {
        validate(newRawPassword);
        if (encoder.matches(newRawPassword, value)) {
            throw new CoreException(ErrorType.BAD_REQUEST,"현재 비밀번호와 동일한 비밀번호는 사용할 수 없습니다");
        }
        return new Password(encoder.encode(newRawPassword));
    }

    public boolean matches(String rawPassword, PasswordEncoder encoder) {
        validateNotBlank(rawPassword);
        return encoder.matches(rawPassword, value);
    }

    private static void validate(String rawPassword) {
        validateNotBlank(rawPassword);

        if (rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST,"비밀번호는 8~16자여야 합니다");
        }

        if (!ALLOWED_CHARS_PATTERN.matcher(rawPassword).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST,"비밀번호는 영문/숫자/특수문자만 가능합니다");
        }
    }

    private static void validateNotBlank(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST,"비밀번호는 필수입니다.");
        }
    }
}
