package com.hjh.assignhub.enrollment;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.assignment.Assignment;
import com.hjh.assignhub.assignment.AssignmentStatus;
import com.hjh.assignhub.assignment.StudentAssignmentService;

import lombok.RequiredArgsConstructor;

// 학생의 수강 중인 강좌 카드 — "내 강좌" 화면과 학생 대시보드가 함께 사용
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyCourseCardService {

    private final EnrollmentService enrollmentService;
    private final StudentAssignmentService studentAssignmentService;

    // 강좌별 과제 수 · 진행중 · 마감 임박(24시간 이내) 개수
    public record CourseCard(Enrollment enrollment, long total, long open, long closingSoon) {
    }

    public List<CourseCard> findMyCourseCards(Long studentId) {
        Map<Long, List<Assignment>> assignmentsByCourse = studentAssignmentService.findMyAssignments(studentId)
                .stream()
                .collect(Collectors.groupingBy(a -> a.getCourse().getId()));

        return enrollmentService.findMyEnrollments(studentId).stream()
                .map(e -> {
                    List<Assignment> assignments = assignmentsByCourse.getOrDefault(e.getCourse().getId(), List.of());
                    return new CourseCard(e,
                            assignments.size(),
                            assignments.stream().filter(a -> a.getStatus() == AssignmentStatus.OPEN).count(),
                            assignments.stream().filter(Assignment::isClosingSoon).count());
                })
                .toList();
    }
}
