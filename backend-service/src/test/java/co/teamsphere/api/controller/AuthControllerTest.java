package co.teamsphere.api.controller;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.teamsphere.api.config.JWTTokenProvider;
import co.teamsphere.api.exception.ProfileImageException;
import co.teamsphere.api.exception.UserException;
import co.teamsphere.api.models.RefreshToken;
import co.teamsphere.api.models.User;
import co.teamsphere.api.repository.UserRepository;
import co.teamsphere.api.request.LoginRequest;
import co.teamsphere.api.request.SignupRequest;
import co.teamsphere.api.response.AuthResponse;
import co.teamsphere.api.services.AuthenticationService;
import co.teamsphere.api.services.RefreshTokenService;
import co.teamsphere.api.utils.GoogleAuthRequest;
import co.teamsphere.api.utils.GoogleUserInfo;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JWTTokenProvider jwtTokenProvider;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuthController authController;

    private SignupRequest signupRequest;
    private LoginRequest loginRequest;
    private AuthResponse successAuthResponse;
    private GoogleAuthRequest googleAuthRequest;

    @BeforeEach
    void setUp() {
        // Setup SecurityContext mock
        SecurityContextHolder.setContext(securityContext);

        // Setup SignupRequest
        signupRequest = new SignupRequest();
        signupRequest.setEmail("test@example.com");
        signupRequest.setUsername("testuser");
        signupRequest.setPassword("Password123");
        signupRequest.setFile(new MockMultipartFile(
                "profile_picture",
                "test.jpg",
                "image/jpeg",
                "test image content".getBytes()
        ));

        // Setup LoginRequest
        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("Password123");

        // Setup success AuthResponse
        successAuthResponse = new AuthResponse("jwt.token.here", "refresh.token.here", true);

        // Setup GoogleAuthRequest
        GoogleUserInfo googleUserInfo = new GoogleUserInfo();
        googleUserInfo.setEmail("test@example.com");
        googleUserInfo.setName("Test User");
        googleUserInfo.setPicture("https://example.com/profile.jpg");

        googleAuthRequest = new GoogleAuthRequest();
        googleAuthRequest.setGoogleUserInfo(googleUserInfo);

        RefreshToken mockRefreshToken = new RefreshToken();
        mockRefreshToken.setRefreshToken("refresh.token.here");
    }

    @Test
    void verifyJwtToken_AuthenticatedUser_ReturnsOk() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);

        // Act
        ResponseEntity<String> response = authController.verifyJwtToken();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void verifyJwtToken_NotAuthenticated_ReturnsUnauthorized() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        // Act
        ResponseEntity<String> response = authController.verifyJwtToken();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo("Token is invalid or not provided.");
    }

    @Test
    void userSignupMethod_ValidRequest_ReturnsCreated() throws Exception {
        // Arrange
        when(authenticationService.signupUser(any(SignupRequest.class))).thenReturn(successAuthResponse);

        // Act
        ResponseEntity<AuthResponse> response = authController.userSignupMethod(signupRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(successAuthResponse);
        verify(authenticationService).signupUser(signupRequest);
    }

    @Test
    void userSignupMethod_UserException_ThrowsUserException() throws Exception {
        // Arrange
        when(authenticationService.signupUser(any(SignupRequest.class)))
                .thenThrow(new UserException("Email already exists"));

        // Act & Assert
        assertThatThrownBy(() -> authController.userSignupMethod(signupRequest))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Email already exists");

        verify(authenticationService).signupUser(signupRequest);
    }

    @Test
    void userSignupMethod_ProfileImageException_ThrowsProfileImageException() throws Exception {
        // Arrange
        when(authenticationService.signupUser(any(SignupRequest.class)))
                .thenThrow(new ProfileImageException("Profile Picture type is not allowed!"));

        // Act & Assert
        assertThatThrownBy(() -> authController.userSignupMethod(signupRequest))
                .isInstanceOf(ProfileImageException.class)
                .hasMessageContaining("Profile Picture type is not allowed!");

        verify(authenticationService).signupUser(signupRequest);
    }

    @Test
    void userLoginMethod_ValidCredentials_ReturnsOk() throws Exception {
        // Arrange
        when(authenticationService.loginUser(loginRequest.getEmail(), loginRequest.getPassword()))
                .thenReturn(successAuthResponse);

        // Act
        ResponseEntity<AuthResponse> response = authController.userLoginMethod(loginRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(successAuthResponse);
        verify(authenticationService).loginUser(loginRequest.getEmail(), loginRequest.getPassword());
    }

    @Test
    void userLoginMethod_InvalidCredentials_ThrowsUserException() throws Exception {
        // Arrange
        when(authenticationService.loginUser(loginRequest.getEmail(), loginRequest.getPassword()))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // Act & Assert
        assertThatThrownBy(() -> authController.userLoginMethod(loginRequest))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Invalid username or password");

        verify(authenticationService).loginUser(loginRequest.getEmail(), loginRequest.getPassword());
    }

    @Test
    void authenticateWithGoogleMethod_NewUser_CreatesUserAndReturnsOk() throws UserException {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        var savedUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .username("Test User")
                .password("encodedPassword")
                .profilePicture("https://example.com/profile.jpg")
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        when(jwtTokenProvider.generateJwtToken(any(Authentication.class))).thenReturn("jwt.token.here");

        var mockRefreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(savedUser)
                .refreshToken("refresh.token.here")
                .build();
        when(refreshTokenService.findByUserId(savedUser.getId().toString())).thenReturn(null);
        when(refreshTokenService.createRefreshToken("test@example.com")).thenReturn(mockRefreshToken);

        // Act
        ResponseEntity<AuthResponse> response = authController.authenticateWithGoogleMethod(googleAuthRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getJwt()).isEqualTo("jwt.token.here");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("refresh.token.here");
        assertThat(response.getBody().isStatus()).isTrue();

        verify(userRepository).findByEmail("test@example.com");
        verify(userRepository).save(any(User.class));
        verify(jwtTokenProvider).generateJwtToken(any(Authentication.class));
        verify(refreshTokenService).createRefreshToken("test@example.com");
    }

    @Test
    void authenticateWithGoogleMethod_ExistingUser_ReturnsOk() throws UserException {
        // Arrange
        var existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .username("Test User")
                .profilePicture("https://example.com/profile.jpg")
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));
        when(jwtTokenProvider.generateJwtToken(any(Authentication.class))).thenReturn("jwt.token.here");

        var existingRefreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(existingUser)
                .refreshToken("existing.refresh.token")
                .expiredAt(Instant.now().plusSeconds(3600))
                .build();

        when(refreshTokenService.findByUserId(existingUser.getId().toString())).thenReturn(existingRefreshToken);

        // Act
        ResponseEntity<AuthResponse> response = authController.authenticateWithGoogleMethod(googleAuthRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getJwt()).isEqualTo("jwt.token.here");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("existing.refresh.token");
        assertThat(response.getBody().isStatus()).isTrue();

        verify(userRepository).findByEmail("test@example.com");
        verify(userRepository, never()).save(any(User.class));
        verify(jwtTokenProvider).generateJwtToken(any(Authentication.class));
        verify(refreshTokenService).findByUserId(existingUser.getId().toString());
        verify(refreshTokenService, never()).createRefreshToken(anyString());
    }

    @Test
    void authenticateWithGoogleMethod_Exception_ReturnsInternalServerError() throws UserException {
        // Arrange
        when(userRepository.findByEmail(anyString())).thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<AuthResponse> response = authController.authenticateWithGoogleMethod(googleAuthRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().isStatus()).isFalse();
        assertThat(response.getBody().getJwt()).contains("Error during Google authentication");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("");

        // Verify repository was called but not the token services
        verify(userRepository).findByEmail("test@example.com");
        verify(jwtTokenProvider, never()).generateJwtToken(any(Authentication.class));
        verify(refreshTokenService, never()).createRefreshToken(anyString());
    }
}
