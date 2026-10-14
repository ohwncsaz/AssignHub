package com.hjh.assignhub.enrollment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.hjh.assignhub.common.FormFieldException;

// 학생 대량 추가용 엑셀 — 양식 파일 생성과 업로드 파일 읽기 (첫 번째 시트, A~C열: 학번 | 이름 | 이메일)
@Component
public class StudentExcel {

    public static final int MAX_ROWS = 300;
    private static final String FILE_FIELD = "file";
    private static final String[] HEADERS = {"학번", "이름", "이메일"};

    public record StudentRow(int rowNumber, String studentNo, String name, String email) {

        public StudentAddForm toForm() {
            return new StudentAddForm(studentNo, name, email);
        }
    }

    public List<StudentRow> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FormFieldException(FILE_FIELD, "엑셀 파일을 선택하세요.");
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".xlsx")) {
            throw new FormFieldException(FILE_FIELD, "엑셀(.xlsx) 파일만 올릴 수 있습니다.");
        }

        List<StudentRow> rows = new ArrayList<>();
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            // 학번이 숫자 셀이어도 2.0260001E7이 아니라 화면에 보이는 그대로 읽는다
            DataFormatter formatter = new DataFormatter();
            for (Row row : sheet) {
                String studentNo = text(formatter, row, 0);
                String name = text(formatter, row, 1);
                String email = text(formatter, row, 2);
                if (studentNo.isEmpty() && name.isEmpty() && email.isEmpty()) {
                    continue;
                }
                if (row.getRowNum() == 0 && HEADERS[0].equals(studentNo)) {
                    continue; // 머리글 행
                }
                rows.add(new StudentRow(row.getRowNum() + 1, studentNo, name, email));
                if (rows.size() > MAX_ROWS) {
                    throw new FormFieldException(FILE_FIELD, "한 번에 최대 " + MAX_ROWS + "명까지 추가할 수 있습니다.");
                }
            }
        } catch (FormFieldException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // 확장자만 .xlsx인 다른 파일, 손상된 파일 등
            throw new FormFieldException(FILE_FIELD, "엑셀 파일을 읽을 수 없습니다. 양식 파일을 내려받아 사용해 주세요.");
        }

        if (rows.isEmpty()) {
            throw new FormFieldException(FILE_FIELD, "추가할 학생이 없습니다. 2행부터 학번·이름·이메일을 입력하세요.");
        }
        return rows;
    }

    // 양식 파일 — 첫 시트는 머리글만 (예시 행이 실제로 등록되지 않도록 예시는 둘째 시트에)
    public byte[] template() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            // 학번 열을 텍스트 형식으로 — 앞자리 0이 사라지지 않게
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));

            Sheet students = workbook.createSheet("학생 목록");
            Row header = students.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }
            students.setDefaultColumnStyle(0, textStyle);
            students.setColumnWidth(0, 14 * 256);
            students.setColumnWidth(1, 14 * 256);
            students.setColumnWidth(2, 30 * 256);
            students.createFreezePane(0, 1);

            Sheet guide = workbook.createSheet("작성 방법");
            String[][] lines = {
                    {"1. [학생 목록] 시트의 2행부터 학번 · 이름 · 이메일을 입력하세요."},
                    {"2. 이미 가입한 학생(같은 학번)은 기존 계정으로 강좌에 등록됩니다."},
                    {"3. 가입하지 않은 학생은 계정이 새로 만들어지고, 초기 비밀번호는 학번입니다. (첫 로그인 때 변경)"},
                    {"4. 한 번에 최대 " + MAX_ROWS + "명까지 올릴 수 있습니다."},
                    {""},
                    {"작성 예시", "", ""},
                    {"학번", "이름", "이메일"},
                    {"20260001", "홍길동", "hong@example.com"},
                    {"20260002", "김철수", "kim@example.com"},
            };
            for (int r = 0; r < lines.length; r++) {
                Row row = guide.createRow(r);
                for (int c = 0; c < lines[r].length; c++) {
                    row.createCell(c).setCellValue(lines[r][c]);
                }
            }
            guide.setColumnWidth(0, 14 * 256);
            guide.setColumnWidth(2, 30 * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String text(DataFormatter formatter, Row row, int column) {
        Cell cell = row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }
}
