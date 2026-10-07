package com.hjh.assignhub.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.auth.LoginUser;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileImageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User student;

    @BeforeEach
    void setUp() {
        student = userRepository.save(User.createStudent("s1@test.com", "pw", "학생1", "20260001"));
    }

    @Test
    @DisplayName("사진을 올리면 저장되고, 다시 로그인하지 않아도 상단바 아바타가 사진으로 바뀐다")
    void upload_showsInTopbar() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(multipart("/profile/image").file(png("me.png")).with(user(new LoginUser(student))).with(csrf()).session(session))
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("successMessage", "프로필 사진을 변경했습니다."));

        assertThat(userRepository.findById(student.getId()).orElseThrow().getProfileImagePath())
                .startsWith("profiles/").endsWith("/me.png");
        mockMvc.perform(get("/").session(session))
                .andExpect(content().string(Matchers.containsString("class=\"img-profile rounded-circle\"")));
        mockMvc.perform(get("/profile/image").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    @DisplayName("확장자만 .png로 바꾼 가짜 이미지, SVG, 2MB 초과 파일은 거부한다")
    void upload_rejectsInvalidFiles() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("image", "fake.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile svg = new MockMultipartFile("image", "x.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile huge = new MockMultipartFile("image", "big.png", "image/png", new byte[(int) ProfileImageService.MAX_SIZE + 1]);

        mockMvc.perform(multipart("/profile/image").file(fake).with(user(new LoginUser(student))).with(csrf()))
                .andExpect(flash().attribute("errorMessage", "이미지 파일이 아니거나 손상된 파일입니다."));
        mockMvc.perform(multipart("/profile/image").file(svg).with(user(new LoginUser(student))).with(csrf()))
                .andExpect(flash().attribute("errorMessage", "png, jpg, gif 형식의 사진만 올릴 수 있습니다."));
        mockMvc.perform(multipart("/profile/image").file(huge).with(user(new LoginUser(student))).with(csrf()))
                .andExpect(flash().attribute("errorMessage", "프로필 사진은 2MB 이하만 올릴 수 있습니다."));

        assertThat(userRepository.findById(student.getId()).orElseThrow().getProfileImagePath()).isNull();
    }

    @Test
    @DisplayName("사진을 삭제하면 기본 아이콘으로 돌아가고, 사진 주소는 404")
    void remove() throws Exception {
        mockMvc.perform(multipart("/profile/image").file(png("me.png")).with(user(new LoginUser(student))).with(csrf()));
        User withImage = userRepository.findById(student.getId()).orElseThrow();

        mockMvc.perform(post("/profile/image/delete").with(user(new LoginUser(withImage))).with(csrf()))
                .andExpect(redirectedUrl("/profile"));

        User removed = userRepository.findById(student.getId()).orElseThrow();
        assertThat(removed.getProfileImagePath()).isNull();
        mockMvc.perform(get("/profile/image").with(user(new LoginUser(removed))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("사진이 없으면 프로필 화면에 기본 아이콘과 업로드 폼이 보인다")
    void profilePage_withoutImage() throws Exception {
        mockMvc.perform(get("/profile").with(user(new LoginUser(student))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("프로필 사진")))
                .andExpect(content().string(Matchers.containsString("action=\"/profile/image\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("사진 삭제"))));
    }

    @Test
    @DisplayName("첫 로그인 비밀번호 변경 전에도 상단바 아바타(사진 보기)는 열리지만, 사진 변경은 막힌다")
    void passwordChangeRequired_allowsOnlyAvatarView() throws Exception {
        User forced = userRepository.save(User.createStudentByInstructor("new@test.com", "pw", "신규", "20269999"));

        mockMvc.perform(get("/profile/image").with(user(new LoginUser(forced))))
                .andExpect(status().isNotFound()); // 비밀번호 화면으로 보내지 않고 정상 처리(사진이 없어서 404)
        mockMvc.perform(multipart("/profile/image").file(png("me.png")).with(user(new LoginUser(forced))).with(csrf()))
                .andExpect(redirectedUrl("/profile/password"));
    }

    private MockMultipartFile png(String name) throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("image", name, "image/png", out.toByteArray());
    }
}
