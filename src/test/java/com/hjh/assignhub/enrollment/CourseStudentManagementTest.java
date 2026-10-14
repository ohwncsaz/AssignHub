package com.hjh.assignhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.assignment.Assignment;
import com.hjh.assignhub.assignment.AssignmentRepository;
import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.common.BusinessException;
import com.hjh.assignhub.common.FormFieldException;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseRepository;
import com.hjh.assignhub.course.CourseService;
import com.hjh.assignhub.enrollment.CourseStudentService.AddResult;
import com.hjh.assignhub.enrollment.StudentImportService.RowResult;
import com.hjh.assignhub.submission.Submission;
import com.hjh.assignhub.submission.SubmissionRepository;
import com.hjh.assignhub.user.Role;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourseStudentManagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseStudentService courseStudentService;

    @Autowired
    private StudentImportService studentImportService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User instructorUser;
    private LoginUser instructor;
    private LoginUser otherInstructor;
    private User existingStudent;
    private Course course;

    @BeforeEach
    void setUp() {
        instructorUser = userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1"));
        instructor = new LoginUser(instructorUser);
        otherInstructor = new LoginUser(userRepository.save(User.createInstructor("t2@test.com", "pw", "강사2")));
        existingStudent = userRepository.save(User.createStudent("old@test.com", "pw", "기존학생", "20260001"));
        course = courseRepository.save(new Course(instructorUser, "자바 스프링", "AB3K7XQ2"));
    }

    // ---------------------------------------------------------------- 1명 추가

    @Test
    @DisplayName("학생 추가: 가입하지 않은 학번이면 계정을 만들고(초기 비밀번호=학번, 변경 필요) 등록한다")
    void add_createsAccount() {
        AddResult result = courseStudentService.addStudent(course.getId(), instructor.getId(),
                new StudentAddForm("20269999", "신규학생", "New@Test.com"));

        assertThat(result.outcome()).isEqualTo(StudentAddOutcome.CREATED);
        User created = userRepository.findByStudentNo("20269999").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.STUDENT);
        assertThat(created.getEmail()).isEqualTo("new@test.com");
        assertThat(passwordEncoder.matches("20269999", created.getPassword())).isTrue();
        assertThat(created.isPasswordChangeRequired()).isTrue();
        assertThat(enrollmentRepository.existsByCourseIdAndStudentId(course.getId(), created.getId())).isTrue();
    }

    @Test
    @DisplayName("학생 추가: 이미 가입한 학번이면 계정을 새로 만들지 않고 기존 계정으로 등록한다")
    void add_enrollsExisting() {
        long before = userRepository.count();

        AddResult result = courseStudentService.addStudent(course.getId(), instructor.getId(),
                new StudentAddForm("20260001", "아무이름", "anything@test.com"));

        assertThat(result.outcome()).isEqualTo(StudentAddOutcome.ENROLLED);
        assertThat(result.studentName()).isEqualTo("기존학생");
        assertThat(userRepository.count()).isEqualTo(before);
        assertThat(existingStudent.isPasswordChangeRequired()).isFalse();
    }

    @Test
    @DisplayName("학생 추가: 이미 수강 중이면 다시 등록하지 않는다")
    void add_alreadyEnrolled() {
        enrollmentRepository.save(new Enrollment(course, existingStudent));

        AddResult result = courseStudentService.addStudent(course.getId(), instructor.getId(),
                new StudentAddForm("20260001", "기존학생", "old@test.com"));

        assertThat(result.outcome()).isEqualTo(StudentAddOutcome.ALREADY_ENROLLED);
    }

    @Test
    @DisplayName("학생 추가: 새 학번인데 이메일을 다른 계정이 쓰고 있으면 거부한다")
    void add_emailTakenByOtherAccount() {
        assertThatThrownBy(() -> courseStudentService.addStudent(course.getId(), instructor.getId(),
                new StudentAddForm("20268888", "다른학생", "old@test.com")))
                .isInstanceOf(FormFieldException.class)
                .extracting("field").isEqualTo("email");
    }

    @Test
    @DisplayName("B5 다른 강사의 강좌에는 학생을 추가할 수 없다")
    void add_otherInstructorDenied() {
        assertThatThrownBy(() -> courseStudentService.addStudent(course.getId(), otherInstructor.getId(),
                new StudentAddForm("20269999", "신규학생", "new@test.com")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("학생 추가 화면: 등록하면 강좌 상세로 이동하고 초기 비밀번호 안내가 나온다")
    void add_viaController() throws Exception {
        mockMvc.perform(post("/instructor/courses/{id}/students", course.getId()).with(user(instructor)).with(csrf())
                        .param("studentNo", "20269999")
                        .param("name", "신규학생")
                        .param("email", "new@test.com"))
                .andExpect(redirectedUrl("/instructor/courses/" + course.getId()))
                .andExpect(flash().attribute("successMessage", Matchers.containsString("초기 비밀번호는 학번")));
    }

    @Test
    @DisplayName("학생 추가 화면: 이미 수강 중인 학생은 학번 칸에 에러를 표시한다")
    void add_viaController_alreadyEnrolled() throws Exception {
        enrollmentRepository.save(new Enrollment(course, existingStudent));

        mockMvc.perform(post("/instructor/courses/{id}/students", course.getId()).with(user(instructor)).with(csrf())
                        .param("studentNo", "20260001")
                        .param("name", "기존학생")
                        .param("email", "old@test.com"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("studentAddForm", "studentNo"));
    }

    // ---------------------------------------------------------------- 엑셀 대량 추가

    @Test
    @DisplayName("엑셀 추가: 줄마다 처리하고, 실패한 줄이 있어도 나머지 줄은 등록된다")
    void import_processesEachRow() throws IOException {
        MockMultipartFile file = xlsx(List.of(
                new String[] {"학번", "이름", "이메일"},
                new String[] {"20261001", "가학생", "ga@test.com"},          // 계정 생성
                new String[] {"20260001", "기존학생", "old@test.com"},        // 기존 계정
                new String[] {"abc", "나학생", "not-an-email"},              // 형식 오류
                new String[] {"", "", ""},                                  // 빈 줄은 건너뜀
                new String[] {"20261002", "다학생", "da@test.com"},           // 계정 생성
                new String[] {"20261001", "가학생", "ga@test.com"}));         // 같은 파일 안에서 중복

        List<RowResult> results = studentImportService.importStudents(course.getId(), instructor.getId(), file);

        assertThat(results).extracting(RowResult::outcome).containsExactly(
                StudentAddOutcome.CREATED,
                StudentAddOutcome.ENROLLED,
                StudentAddOutcome.FAILED,
                StudentAddOutcome.CREATED,
                StudentAddOutcome.ALREADY_ENROLLED);
        assertThat(results.get(2).rowNumber()).isEqualTo(4);
        assertThat(results.get(2).message()).contains("학번은 숫자", "이메일 형식");
        assertThat(courseStudentCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("엑셀 추가: 학번이 숫자 셀이어도 그대로(지수 표기 없이) 읽는다")
    void import_numericStudentNoCell() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Row row = workbook.createSheet().createRow(0);
        row.createCell(0).setCellValue(20261234d);
        row.createCell(1).setCellValue("숫자학번");
        row.createCell(2).setCellValue("num@test.com");

        List<RowResult> results = studentImportService.importStudents(course.getId(), instructor.getId(), toFile(workbook, "num.xlsx"));

        assertThat(results.get(0).outcome()).isEqualTo(StudentAddOutcome.CREATED);
        assertThat(userRepository.findByStudentNo("20261234")).isPresent();
    }

    @Test
    @DisplayName("엑셀 추가: .xlsx가 아니거나 읽을 수 없는 파일은 파일 입력칸 에러로 거부한다")
    void import_invalidFile() {
        MockMultipartFile csv = new MockMultipartFile("file", "students.csv", "text/csv", "a,b,c".getBytes());
        MockMultipartFile fake = new MockMultipartFile("file", "fake.xlsx", "application/octet-stream", "not excel".getBytes());

        assertThatThrownBy(() -> studentImportService.importStudents(course.getId(), instructor.getId(), csv))
                .isInstanceOf(FormFieldException.class).hasMessageContaining(".xlsx");
        assertThatThrownBy(() -> studentImportService.importStudents(course.getId(), instructor.getId(), fake))
                .isInstanceOf(FormFieldException.class).hasMessageContaining("읽을 수 없습니다");
    }

    @Test
    @DisplayName("엑셀 추가 화면: 결과 표와 결과별 개수를 보여준다")
    void import_viaController() throws Exception {
        MockMultipartFile file = xlsx(List.of(
                new String[] {"학번", "이름", "이메일"},
                new String[] {"20261001", "가학생", "ga@test.com"},
                new String[] {"bad", "나학생", "na@test.com"}));

        mockMvc.perform(multipart("/instructor/courses/{id}/students/import", course.getId())
                        .file(file).with(user(instructor)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("계정 생성 후 등록 1")))
                .andExpect(content().string(Matchers.containsString("실패 1")))
                .andExpect(content().string(Matchers.containsString("학번은 숫자 4~20자리로 입력하세요.")));
    }

    @Test
    @DisplayName("엑셀 양식 파일을 내려받으면 첫 시트에 머리글(학번·이름·이메일)만 있다")
    void template_download() throws Exception {
        byte[] body = mockMvc.perform(get("/instructor/courses/{id}/students/template", course.getId()).with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString(".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("학번");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("이메일");
            assertThat(sheet.getLastRowNum()).isZero();
        }
    }

    // ---------------------------------------------------------------- 내보내기 · 참여코드 재발급

    @Test
    @DisplayName("내보내기: 수강 등록만 삭제되고 학생 계정은 남는다")
    void remove_success() throws Exception {
        enrollmentRepository.save(new Enrollment(course, existingStudent));

        mockMvc.perform(post("/instructor/courses/{cid}/students/{sid}/remove", course.getId(), existingStudent.getId())
                        .with(user(instructor)).with(csrf()))
                .andExpect(redirectedUrl("/instructor/courses/" + course.getId()))
                .andExpect(flash().attribute("successMessage", "기존학생 학생을 강좌에서 내보냈습니다."));

        assertThat(enrollmentRepository.existsByCourseIdAndStudentId(course.getId(), existingStudent.getId())).isFalse();
        assertThat(userRepository.findById(existingStudent.getId())).isPresent();
    }

    @Test
    @DisplayName("내보내기: 이 강좌에 제출물이 있는 학생은 내보낼 수 없다")
    void remove_withSubmissionDenied() {
        enrollmentRepository.save(new Enrollment(course, existingStudent));
        Assignment assignment = assignmentRepository.save(new Assignment(course, "과제", "내용",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), 100, null));
        submissionRepository.save(new Submission(assignment, existingStudent, "제출", null));

        assertThatThrownBy(() -> courseStudentService.removeStudent(course.getId(), instructor.getId(), existingStudent.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("제출물이 있는 학생은 내보낼 수 없습니다.");
    }

    @Test
    @DisplayName("참여코드 재발급: 새 코드로 바뀌고 이전 코드로는 찾을 수 없다")
    void regenerateJoinCode() throws Exception {
        mockMvc.perform(post("/instructor/courses/{id}/join-code", course.getId()).with(user(instructor)).with(csrf()))
                .andExpect(redirectedUrl("/instructor/courses/" + course.getId()));

        Course updated = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(updated.getJoinCode()).isNotEqualTo("AB3K7XQ2").matches("[A-Z2-9]{8}");
        assertThat(courseRepository.findByJoinCode("AB3K7XQ2")).isEmpty();
    }

    @Test
    @DisplayName("B5 다른 강사는 참여코드를 재발급할 수 없다")
    void regenerateJoinCode_otherInstructorDenied() {
        assertThatThrownBy(() -> courseService.regenerateJoinCode(course.getId(), otherInstructor.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("XSS: 학생 이름에 스크립트를 넣어도 강좌 상세에서 실행 코드로 들어가지 않는다")
    void detail_escapesStudentName() throws Exception {
        User attacker = userRepository.save(User.createStudent("x@test.com", "pw", "');alert(1);('", "20267777"));
        enrollmentRepository.save(new Enrollment(course, attacker));

        mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("confirm(''"))))
                .andExpect(content().string(Matchers.containsString("data-name=\"&#39;);alert(1);(&#39;\"")));
    }

    private long courseStudentCount() {
        return enrollmentRepository.findStudentsOfCourse(course.getId()).size();
    }

    private MockMultipartFile xlsx(List<String[]> rows) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("학생 목록");
        for (int r = 0; r < rows.size(); r++) {
            Row row = sheet.createRow(r);
            for (int c = 0; c < rows.get(r).length; c++) {
                row.createCell(c).setCellValue(rows.get(r)[c]);
            }
        }
        return toFile(workbook, "students.xlsx");
    }

    private MockMultipartFile toFile(Workbook workbook, String name) throws IOException {
        try (workbook; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return new MockMultipartFile("file", name,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}
