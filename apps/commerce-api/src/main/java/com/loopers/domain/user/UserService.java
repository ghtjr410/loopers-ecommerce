package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User signUp(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다");
        }

        User user = User.create(loginId, rawPassword, name, birthDate, email, passwordEncoder);
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long id, String newRawPassword) {
        User user = getById(id);
        user.changePassword(newRawPassword, passwordEncoder);
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다"));
    }

    public User authenticate(String loginId, String rawPassword) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다"));
        if (!user.matchesPassword(rawPassword, passwordEncoder)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다");
        }
        return user;
    }
}
