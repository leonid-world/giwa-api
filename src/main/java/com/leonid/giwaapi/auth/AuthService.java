package com.leonid.giwaapi.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.leonid.giwaapi.company.Company;
import com.leonid.giwaapi.company.CompanyMapper;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final CompanyMapper companyMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, CompanyMapper companyMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.companyMapper = companyMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userMapper.findByEmail(request.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        Company company = new Company(null, request.companyName(), request.businessNumber());
        companyMapper.insert(company);
        User user = new User(null, company.companyId(), request.email(), passwordEncoder.encode(request.password()), request.userName(), null);
        userMapper.insert(user);
        return authenticate(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userMapper.findByEmail(request.email())
                .filter(found -> passwordEncoder.matches(request.password(), found.passwordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return authenticate(user);
    }

    public UserResponse me(String email) {
        return userMapper.findByEmail(email)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private AuthResponse authenticate(User user) {
        return new AuthResponse(jwtService.createToken(user), UserResponse.from(user));
    }
}
